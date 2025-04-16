package zio.metrics.connectors.statsd

import zio.{ULayer, _}

import scala.annotation.unused

abstract class StatsdConfig

final case class StatsdIpConfig(host: String, port: Int) extends StatsdConfig

final case class StatsdUdsConfig(path: String) extends StatsdConfig

object StatsdIpConfig {

  val default: StatsdIpConfig = StatsdIpConfig("localhost", 8125)

  @unused
  val defaultLayer: ULayer[StatsdIpConfig] = ZLayer.succeed(default)
}
