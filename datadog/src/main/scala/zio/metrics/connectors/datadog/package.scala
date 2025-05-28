package zio.metrics.connectors

import zio._
import zio.internal.RingBuffer
import zio.metrics.{MetricClient, MetricKey, MetricKeyType}
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.{DatagramSocketClient, StatsdClient}

package object datadog {

  @deprecated("Use zio.metrics.connectors.datadog.live instead", "2.4.0")
  lazy val datadogLayer: ZLayer[DatadogConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config         <- ZIO.service[DatadogConfig]
        publisherConfig = DatadogPublisherConfig(
                            config.histogramSendInterval,
                            config.maxBatchedMetrics,
                            config.maxQueueSize,
                            config.containerId,
                            config.entityId,
                            config.sendUnchanged,
                          )
        clt            <- DatagramSocketClient.make.provideSome[Scope](ZLayer.succeed(config))
        queue           = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener        = new DataDogListener(queue)
        _              <- Unsafe.unsafe(unsafe =>
                            ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)(unsafe)))(_ =>
                              ZIO.succeed(MetricClient.removeListener(listener)(unsafe)),
                            ),
                          )
        _              <- DataDogEventProcessor.make(clt, queue).provideSome[MetricsConfig](ZLayer.succeed(publisherConfig))
        _              <- MetricsClient.make(datadogHandler(clt, publisherConfig))
      } yield (),
    )

  lazy val live: ZLayer[StatsdClient & DatadogPublisherConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[DatadogPublisherConfig]
        clt     <- ZIO.service[StatsdClient]
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener = new DataDogListener(queue)
        _       <- Unsafe.unsafe(unsafe =>
                     ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)(unsafe)))(_ =>
                       ZIO.succeed(MetricClient.removeListener(listener)(unsafe)),
                     ),
                   )
        _       <- DataDogEventProcessor.make(clt, queue)
        _       <- MetricsClient.make(datadogHandler(clt, config))
      } yield (),
    )

  private def eventFilter(config: DatadogPublisherConfig): MetricEvent => Boolean =
    if (config.sendUnchanged) {
      !_.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]
    } else {
      case MetricEvent.Unchanged(_, _, _) => false
      case e                              => !e.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]
    }

  private[connectors] def datadogHandler(
    client: StatsdClient,
    config: DatadogPublisherConfig,
  ): Iterable[MetricEvent] => UIO[Unit] = events => {
    val encoder = DatadogEncoder.encoder(config)

    val send = ZIO
      .foreachDiscard(events.filter(eventFilter(config)))(evt =>
        for {
          encoded <- encoder(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(client.send(encoded)))
        } yield (),
      )

    send.ignore
  }

}
