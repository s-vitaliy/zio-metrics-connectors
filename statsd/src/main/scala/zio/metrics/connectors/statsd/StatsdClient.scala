package zio.metrics.connectors.statsd

import java.net.{InetSocketAddress, UnixDomainSocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import scala.util.{Failure, Success, Try}
import zio._

trait StatsdClient {
  private[connectors] def send(chunk: Chunk[Byte]): Long
}

private[connectors] object StatsdClient {

  private class Live(channel: DatagramChannel) extends StatsdClient {

    override def send(chunk: Chunk[Byte]): Long =
      write(chunk.toArray)

    private def write(ab: Array[Byte]): Long =
      Try(channel.write(ByteBuffer.wrap(ab)).toLong) match {
        case Success(value) =>
          // println(s"Sent UDP data [$value]")
          value
        case Failure(_)     =>
          // t.printStackTrace()
          0L
      }

  }

  private def channelIP(host: String, port: Int): ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.connect(new InetSocketAddress(host, port))
      channel.configureBlocking(false)
      channel
    })

  private def channelUDS(path: String): ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.connect(UnixDomainSocketAddress.of(path))
      channel.configureBlocking(false)
      channel
    })

  private[connectors] def make: ZIO[Scope & StatsdConfig, Nothing, StatsdClient] =
    for {
      config  <- ZIO.service[StatsdConfig]
      channel <- (config match {
        case StatsdIpConfig(host, port) => channelIP(host, port)
        case StatsdUdsConfig(path)      => channelUDS(path)
      }).orDie
      client   = new Live(channel)
    } yield client

}
