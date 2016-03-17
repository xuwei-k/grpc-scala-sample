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

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import io.grpc.examples.helloworld.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.helloworld.GreeterGrpc.Greeter
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.examples.helloworld.helloworld.HelloReply
import java.util.logging.Logger
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A simple server that like [[io.grpc.examples.helloworld.HelloWorldServer]].
 * You can get and response any header in [[io.grpc.examples.header.HeaderServerInterceptor]].
 */
object CustomHeaderServer {
  private val logger: Logger = Logger.getLogger(classOf[CustomHeaderServer].getName)
  private val port: Int = 50051

  /**
   * Main launches the server from the command line.
   */
  def main(args: Array[String]) {
    val server = new CustomHeaderServer
    server.start()
    server.blockUntilShutdown()
  }
}

class CustomHeaderServer {
  private var server: Server = null

  private def start() {
    server = ServerBuilder.forPort(CustomHeaderServer.port).addService(
      ServerInterceptors.intercept(
        GreeterGrpc.bindService(new GreeterImpl, ExecutionContext.global),
        new HeaderServerInterceptor
      )
    ).build.start
    CustomHeaderServer.logger.info("Server started, listening on " + CustomHeaderServer.port)
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        CustomHeaderServer.this.stop()
        System.err.println("*** server shut down")
      }
    })
  }

  private def stop() {
    if (server != null) {
      server.shutdown
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private def blockUntilShutdown() {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class GreeterImpl extends Greeter {
    def sayHello(req: HelloRequest): Future[HelloReply] = {
      Future.successful(HelloReply("Hello " + req.name))
    }
  }

}
