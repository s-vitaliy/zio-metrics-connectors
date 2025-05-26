package zio.metrics.connectors.datadog

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.file.{Files, Path}

import zio._
import zio.test._
import zio.test.TestAspect._

import jnr.unixsocket.{UnixDatagramChannel, UnixSocketAddress}

object DogStatsdClientSpec extends ZIOSpecDefault {

  override def spec = suite("The DogStatsdClient should")(
    writeViaNetwork,
    writeViaUds,
  ) @@ timed @@ timeoutWarning(60.seconds) @@ TestAspect.timeout(90.seconds) @@ TestAspect.withLiveClock

  private val writeViaNetwork = test("be able to write data using network") {
    for {
      promise <- Promise.make[Nothing, Unit]
      client  <- DogStatsdClient.make.provideSome[Scope](ZLayer.succeed(DatadogNetworkConfig("localhost", 8181)))
      server  <- testServer(
                   ZIO.attempt {
                     val ch = DatagramChannel.open()
                     ch.bind(new InetSocketAddress("localhost", 8181))
                     ch
                   },
                   promise,
                 )
      _       <- ZIO.attempt(client.send(Chunk.fromArray("testMetric:1|g".getBytes)))
      result  <- server.join
    } yield assertTrue(result == "testMetric:1|g")
  }

  private val writeViaUds = test("be able to write data to unix domain socket") {
    for {
      promise    <- Promise.make[Nothing, Unit]
      socketPath <- getTempPath
      client     <- DogStatsdClient.make.provideSome[Scope](ZLayer.succeed(DatadogUdsConfigConfig(socketPath)))
      server     <- testServer(
                      ZIO.attempt {
                        val ch = UnixDatagramChannel.open()
                        ch.bind(new UnixSocketAddress(socketPath))
                        ch
                      },
                      promise,
                    )
      _          <- promise.await
      _          <- ZIO.attempt(client.send(Chunk.fromArray("testMetric:1|g".getBytes)))
      result     <- server.join
    } yield assertTrue(result == "testMetric:1|g")
  } @@ TestAspect.around(clearUnixDomainSocket, clearUnixDomainSocket)

  private def getTempPath = for {
    maybePath <- System.property("java.io.tmpdir")
  } yield maybePath match {
    case None => "/tmp/dogstatsd.sock"
    case path => s"$path/dogstatsd.sock"
  }

  private def clearUnixDomainSocket: ZIO[Any, Nothing, Unit] =
    for {
      path <- getTempPath.orDie
      _    <- ZIO.succeed(Files.deleteIfExists(Path.of(path)))
    } yield ()

  private def testServer(createChannel: Task[DatagramChannel], promise: Promise[Nothing, Unit]) =
    for {
      channel <- createChannel
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
