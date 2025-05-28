package zio.metrics.connectors.statsd

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import scala.util.{Failure, Success, Try}

import zio._

import jnr.unixsocket.{UnixDatagramChannel, UnixSocketAddress}

private trait DogStatsdSocketWriter {
  private[connectors] def write(byteBuffer: ByteBuffer): Try[Long]
}

private[connectors] object DatagramSocketClient {

  private class Live(writer: DogStatsdSocketWriter) extends StatsdClient {

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

  private class UdsWriter(channel: DatagramChannel, address: UnixSocketAddress) extends DogStatsdSocketWriter {
    private[connectors] def write(byteBuffer: ByteBuffer): Try[Long] = Try(channel.send(byteBuffer, address).toLong)
  }

  private def udsWriter(path: String): ZIO[Scope, Throwable, DogStatsdSocketWriter] =
    for {
      channel <- ZIO.fromAutoCloseable(ZIO.attempt {
                   val channel = UnixDatagramChannel.open()
                   channel.configureBlocking(false)
                   channel
                 })
    } yield new UdsWriter(channel, new UnixSocketAddress(path))

  private[connectors] def make: ZIO[Scope & DatagramSocketConfig, Nothing, StatsdClient] =
    for {
      config <- ZIO.service[DatagramSocketConfig]
      writer <- udsWriter(config.path).orDie
    } yield new Live(writer)

}
