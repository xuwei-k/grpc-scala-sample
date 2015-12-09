package io.grpc.examples.helloworld

import java.util.concurrent.TimeUnit
import java.util.logging.{Level, Logger}

import io.grpc.examples.helloworld.hello_world.{HelloRequest, GreeterGrpc}
import io.grpc.examples.helloworld.hello_world.GreeterGrpc.GreeterBlockingStub
import io.grpc.{ManagedChannelBuilder, ManagedChannel}

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.9.0/examples/src/main/java/io/grpc/examples/helloworld/HelloWorldClient.java]]
 */
object HelloWorldClient {
  def apply(host: String, port: Int): HelloWorldClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val blockingStub = GreeterGrpc.blockingStub(channel)
    new HelloWorldClient(channel, blockingStub)
  }

  def main(args: Array[String]): Unit = {
    val client = HelloWorldClient("localhost", 50051)
    try {
      val user = args.headOption.getOrElse("world")
      client.greet(user)
    } finally {
      client.shutdown()
    }
  }
}

class HelloWorldClient private(
  private val channel: ManagedChannel,
  private val blockingStub: GreeterBlockingStub
) {
  private[this] val logger = Logger.getLogger(classOf[HelloWorldClient].getName)

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  /** Say hello to server. */
  def greet(name: String) {
    try {
      logger.info("Will try to greet " + name + " ...")
      val request = HelloRequest(name = name)
      val response = blockingStub.sayHello(request)
      logger.info("Greeting: " + response.message)
    }
    catch {
      case e: RuntimeException =>
        logger.log(Level.WARNING, "RPC failed", e)
    }
  }


}
