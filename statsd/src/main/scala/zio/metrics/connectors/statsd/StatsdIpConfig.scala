package zio.metrics.connectors.statsd

import zio.{ULayer, _}

final case class StatsdIpConfig(
  host: String,
  port: Int)

object StatsdIpConfig {

  val default: StatsdIpConfig =
    StatsdIpConfig("localhost", 8125)

  val defaultLayer: ULayer[StatsdIpConfig] = ZLayer.succeed(default)
}
