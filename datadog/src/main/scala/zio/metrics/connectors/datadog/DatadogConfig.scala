package zio.metrics.connectors.datadog

import java.time.Duration

import zio.{ULayer, ZLayer}

/**
 * Base trait for Datadog configuration
 */
trait DatadogConfig {
  val histogramSendInterval: Option[Duration]
  val maxBatchedMetrics: Int
  val containerId: Option[String]
  val entityId: Option[String]
  val maxQueueSize: Int
  val sendUnchanged: Boolean
}

/**
 * Datadog Specific configuration used for sending using UDP over a network connection.
 *
 * @param host
 *  Agent host name
 * @param port
 *  Agent port
 * @param histogramSendInterval
 *  Override for when the distributions should be sent faster than the general metrics frequency.
 *  This is typically with an app that generates lots of distributions, but doesn't want to send other metrics
 *  types, such as gauges, too frequently
 * @param maxBatchedMetrics
 *  The maximum number of metrics to batch before sending. This affects packet size
 * @param maxQueueSize
 *  The maximum number of metrics stored in the queue. This affects memory usage
 * @param containerId
 *  An optional docker container ID
 * @param entityId
 *  An optional entity ID value used with an internal tag for tracking client entity
 */
final case class DatadogNetworkConfig(
  host: String,
  port: Int,
  histogramSendInterval: Option[Duration] = None,
  maxBatchedMetrics: Int = 10,
  maxQueueSize: Int = 100000,
  containerId: Option[String] = None,
  entityId: Option[String] = None,
  sendUnchanged: Boolean = false)
    extends DatadogConfig

/**
 * Datadog Specific configuration used for sending metrics using UDP over a Unix Domain Socket (UDS).
 *
 * @param path
 *  Path to the Unix Domain Socket (UDS) for the Datadog agent
 * @param histogramSendInterval
 *  Override for when the distributions should be sent faster than the general metrics frequency.
 *  This is typically with an app that generates lots of distributions, but doesn't want to send other metrics
 *  types, such as gauges, too frequently
 * @param maxBatchedMetrics
 *  The maximum number of metrics to batch before sending. This affects packet size
 * @param maxQueueSize
 *  The maximum number of metrics stored in the queue. This affects memory usage
 * @param containerId
 *  An optional docker container ID
 * @param entityId
 *  An optional entity ID value used with an internal tag for tracking client entity
 */
final case class DatadogUdsConfigConfig(
  path: String,
  histogramSendInterval: Option[Duration] = None,
  maxBatchedMetrics: Int = 10,
  maxQueueSize: Int = 100000,
  containerId: Option[String] = None,
  entityId: Option[String] = None,
  sendUnchanged: Boolean = false)
    extends DatadogConfig

object DatadogConfig {

  val default: DatadogNetworkConfig =
    DatadogNetworkConfig(
      host = "localhost",
      port = 8125,
      histogramSendInterval = None,
    )

  val defaultLayer: ULayer[DatadogConfig] = ZLayer.succeed(default)
}
