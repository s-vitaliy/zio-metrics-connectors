package zio.metrics.connectors.statsd

import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.file.{Files, Path}

import zio._
import zio.test._
import zio.test.TestAspect._

import jnr.unixsocket.{UnixDatagramChannel, UnixSocketAddress}

object DatagramSocketClientSpec extends ZIOSpecDefault {

  override def spec = suite("The DatagramSocketClient should")(
    writeViaUds,
  ) @@ timed @@ timeoutWarning(60.seconds) @@ TestAspect.timeout(90.seconds) @@ TestAspect.withLiveClock

  private val writeViaUds = test("be able to write data to unix domain socket") {
    for {
      // Arrange
      promise    <- Promise.make[Nothing, Unit]
      socketPath <- getTempPath
      client     <- DatagramSocketClient.make.provideSome[Scope](ZLayer.succeed(DatagramSocketConfig(socketPath)))
      server     <- testServer(
                      ZIO.attempt {
                        val ch = UnixDatagramChannel.open()
                        ch.bind(new UnixSocketAddress(socketPath))
                        ch
                      },
                      promise,
                    )
      _          <- promise.await

      // Act
      _      <- ZIO.attempt(client.send(Chunk.fromArray("testMetric:1|g".getBytes)))
      result <- server.join

      // Assert
    } yield assertTrue(result == "testMetric:1|g")
  } @@ TestAspect.around(clearUnixDomainSocket, clearUnixDomainSocket)

  /**
   * Retrieves the temporary path for the Unix domain socket.
   * If the system property `java.io.tmpdir` is not set, defaults to `/tmp/socket.sock`.
   * This method is used to ensure that the socket path is consistent across different environments and
   * the socket path can be overridden if needed.
   * @return A ZIO effect that returns the path as a String
   */
  private def getTempPath = for {
    maybePath <- System.property("java.io.tmpdir")
  } yield maybePath match {
    case None => "/tmp/socket.sock"
    case path => s"$path/socket.sock"
  }

  /**
   * Clears the Unix domain socket by deleting the file at the temporary path.
   * This method is used to ensure that the socket is not left in an inconsistent state and adds the
   * ability to run tests multiple times without interference.
   * @return A ZIO effect that completes when the socket is cleared
   */
  private def clearUnixDomainSocket: ZIO[Any, Nothing, Unit] =
    for {
      path <- getTempPath.orDie
      _    <- ZIO.succeed(Files.deleteIfExists(Path.of(path)))
    } yield ()

  /**
   * Creates a test server that listens for incoming datagrams on the specified channel.
   * Signals completion via the provided promise when the server is ready.
   * @param channel ZIO effect to create a DatagramChannel
   * @param promise Promise to signal when the server is ready
   * @return A ZIO effect that returns the server's response as a String
   */
  private def testServer(channel: Task[DatagramChannel], promise: Promise[Nothing, Unit]) =
    for {
      channel <- channel
      buffer  <- ZIO.succeed(ByteBuffer.allocate(1024))
      server  <- ZIO.attempt {
                   channel.receive(buffer)
                   buffer.flip()
                   val bytes = new Array[Byte](buffer.remaining())
                   buffer.get(bytes)
                   new String(bytes)
                 }.fork
      _       <- promise.succeed(())
    } yield server
}
