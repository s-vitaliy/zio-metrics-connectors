package zio.metrics.connectors.datadog

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import scala.util.{Failure, Success, Try}

import zio._

import jnr.unixsocket.{UnixDatagramChannel, UnixSocketAddress}

trait DogStatsdClient {
  private[connectors] def send(chunk: Chunk[Byte]): Long
}

private trait DogStatsdSocketWriter {
  private[connectors] def write(byteBuffer: ByteBuffer): Try[Long]
}

private[connectors] object DogStatsdClient {

  private class Live(writer: DogStatsdSocketWriter) extends DogStatsdClient {

    override def send(chunk: Chunk[Byte]): Long =
      writer.write(ByteBuffer.wrap(chunk.toArray)) match {
        case Success(value) =>
          // println(s"Sent UDP data [$value]")
          value
        case Failure(_)     =>
          // t.printStackTrace()
          0L
      }
  }

  private class NetworkWriter(channel: DatagramChannel) extends DogStatsdSocketWriter {
    private[connectors] def write(byteBuffer: ByteBuffer): Try[Long] = Try(channel.write(byteBuffer).toLong)
  }

  private class UdsWriter(channel: DatagramChannel, address: UnixSocketAddress) extends DogStatsdSocketWriter {
    private[connectors] def write(byteBuffer: ByteBuffer): Try[Long] = Try(channel.send(byteBuffer, address).toLong)
  }

  private def networkWriter(host: String, port: Int): ZIO[Scope, Throwable, DogStatsdSocketWriter] =
    for {
      channel <- ZIO.fromAutoCloseable(ZIO.attempt {
                   val channel = DatagramChannel.open()
                   channel.connect(new InetSocketAddress(host, port))
                   channel.configureBlocking(false)
                   channel
                 })
    } yield new NetworkWriter(channel)

  private def udsWriter(path: String): ZIO[Scope, Throwable, DogStatsdSocketWriter] =
    for {
      channel <- ZIO.fromAutoCloseable(ZIO.attempt {
                   val channel = UnixDatagramChannel.open()
                   channel.configureBlocking(false)
                   channel
                 })
    } yield new UdsWriter(channel, new UnixSocketAddress(path))

  private[connectors] def make: ZIO[Scope & DatadogConfig, Nothing, DogStatsdClient] =
    for {
      config <- ZIO.service[DatadogConfig]
      writer <- config match {
                  case c: DatadogNetworkConfig   => networkWriter(c.host, c.port).orDie
                  case c: DatadogUdsConfigConfig => udsWriter(c.path).orDie

                }
      client  = new Live(writer)
    } yield client

}
