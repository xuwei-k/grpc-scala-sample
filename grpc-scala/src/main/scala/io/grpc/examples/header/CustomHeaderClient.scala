/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.grpc.examples.header

import io.grpc.Channel
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.examples.helloworld.hello_world.GreeterGrpc
import io.grpc.examples.helloworld.hello_world.HelloRequest
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A simple client that like [[io.grpc.examples.helloworld.HelloWorldClient]].
 * This client can help you create custom headers.
 */
object CustomHeaderClient {
  private val logger: Logger = Logger.getLogger(classOf[CustomHeaderClient].getName)

  /**
   * Main start the client from the command line.
   */
  @throws(classOf[Exception])
  def main(args: Array[String]) {
    val client: CustomHeaderClient = new CustomHeaderClient("localhost", 50051)
    try {
      var user: String = "world"
      if (args.length > 0) {
        user = args(0)
      }
      client.greet(user)
    } finally {
      client.shutdown()
    }
  }
}

/**
 * A custom client.
 */
class CustomHeaderClient(host: String, port: Int) {
  private final val originChannel: ManagedChannel =
    ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
  private final val blockingStub: GreeterGrpc.GreeterBlockingStub = {
    val interceptor: ClientInterceptor = new HeaderClientInterceptor
    val channel: Channel = ClientInterceptors.intercept(originChannel, interceptor)
    GreeterGrpc.blockingStub(channel)
  }

  private def shutdown() {
    originChannel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  /**
   * A simple client method that like [[io.grpc.examples.helloworld.HelloWorldClient]].
   */
  private def greet(name: String) {
    try {
      CustomHeaderClient.logger.info("Will try to greet " + name + " ...")
      val request = HelloRequest(name)
      val response = blockingStub.sayHello(request)
      CustomHeaderClient.logger.info("Greeting: " + response.message)
    }
    catch {
      case e: RuntimeException =>
        CustomHeaderClient.logger.log(Level.WARNING, "RPC failed", e)
    }
  }
}
