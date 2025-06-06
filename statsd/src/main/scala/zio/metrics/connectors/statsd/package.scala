package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {

  @deprecated("Use zio.metrics.connectors.datadog.live instead", "2.4.0")
  lazy val statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      StatsdClient.make.flatMap(clt => MetricsClient.make(statsdHandler(clt))).unit,
    )

  lazy val live: ZLayer[StatsdConfig & MetricsConfig, Nothing, StatsdClient] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[StatsdConfig]
        client <- StatsdClient.make.provideSome[Scope](ZLayer.succeed(config))
      } yield client
    }

  lazy val liveDatagram: ZLayer[DatagramSocketConfig & MetricsConfig, Nothing, StatsdClient] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[DatagramSocketConfig]
        client <- DatagramSocketClient.make.provideSome[Scope](ZLayer.succeed(config))
      } yield client
    }

  private[connectors] def statsdHandler(clt: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter: MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _                              => true
    }

    val send = ZIO
      .foreachDiscard(events.filter(evtFilter))(evt =>
        for {
          encoded <- StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(clt.send(encoded)))
        } yield (),
      )

    // TODO: Do we want to at least log a problem sending the metrics ?
    send.catchAll(_ => ZIO.unit)
  }
}
