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
package io.grpc.examples.routeguide

import com.google.common.util.concurrent.SettableFuture
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.examples.routeguide.route_guide._
import io.grpc.stub.StreamObserver
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.9.0/examples/src/main/java/io/grpc/examples/routeguide/RouteGuideClient.java]]
 */
object RouteGuideClient {
  private val logger: Logger = Logger.getLogger(classOf[RouteGuideClient].getName)

  def main(args: Array[String]) {
    val client = RouteGuideClient("localhost", 8980)
    try {
      client.getFeature(409146138, -746188906)
      client.getFeature(0, 0)
      client.listFeatures(400000000, -750000000, 420000000, -730000000)
      client.recordRoute(RouteGuideUtil.parseFeatures(RouteGuideUtil.getDefaultFeaturesFile), 10)
      client.routeChat()
    } finally {
      client.shutdown()
    }
  }

  def apply(host: String, port: Int): RouteGuideClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    new RouteGuideClient(
      channel = channel,
      blockingStub = RouteGuideGrpc.blockingStub(channel),
      asyncStub = RouteGuideGrpc.stub(channel)
    )
  }

}

class RouteGuideClient private (
  channel: ManagedChannel,
  blockingStub: RouteGuideGrpc.RouteGuideBlockingStub,
  asyncStub: RouteGuideGrpc.RouteGuideStub
){

  private def info(msg: String, params: Any*): Unit = {
    RouteGuideClient.logger.log(Level.INFO, msg, params.map(_.asInstanceOf[AnyRef]).toArray)
  }

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def getFeature(lat: Int, lon: Int): Unit = {
    try {
      info("*** GetFeature: lat={0} lon={1}", lat, lon)
      val request = Point(latitude = lat, longitude = lon)
      val feature = blockingStub.getFeature(request)
      if (RouteGuideUtil.exists(feature)) {
        info("Found feature called \"{0}\" at {1}, {2}", feature.name, RouteGuideUtil.getLatitude(feature.getLocation), RouteGuideUtil.getLongitude(feature.getLocation))
      }
      else {
        info("Found no feature at {0}, {1}", RouteGuideUtil.getLatitude(feature.getLocation), RouteGuideUtil.getLongitude(feature.getLocation))
      }
    }
    catch {
      case e: RuntimeException =>
        RouteGuideClient.logger.log(Level.WARNING, "RPC failed", e)
        throw e
    }
  }

  def listFeatures(lowLat: Int, lowLon: Int, hiLat: Int, hiLon: Int): Unit = {
    try {
      info("*** ListFeatures: lowLat={0} lowLon={1} hiLat={2} hiLon={3}", lowLat, lowLon, hiLat, hiLon)
      val request = Rectangle(lo = Some(Point(latitude = lowLat, longitude = lowLon)), hi = Some(Point(latitude = hiLat, longitude = hiLon)))
      val features = blockingStub.listFeatures(request)
      val responseLog = features.mkString("Result: ", "", "")
      info(responseLog.toString)
    }
    catch {
      case e: RuntimeException =>
        RouteGuideClient.logger.log(Level.WARNING, "RPC failed", e)
        throw e
    }
  }

  def recordRoute(features: List[Feature], numPoints: Int): Unit = {
    info("*** RecordRoute")
    val finishFuture = SettableFuture.create[Unit]
    val responseObserver = new StreamObserver[RouteSummary] {
      def onNext(summary: RouteSummary) {
        info("Finished trip with {0} points. Passed {1} features. " + "Travelled {2} meters. It took {3} seconds.", summary.pointCount, summary.featureCount, summary.distance, summary.elapsedTime)
      }

      def onError(t: Throwable) {
        finishFuture.setException(t)
      }

      def onCompleted(): Unit = {
        finishFuture.set(())
      }
    }
    val requestObserver = asyncStub.recordRoute(responseObserver)
    try {
      val numMsg= new StringBuilder
      val rand = new Random

      var i = 0
      var done = false
      while(i < numPoints && !done) {
        val index = rand.nextInt(features.size)
        val point = features(index).location.getOrElse(sys.error(s"location is None $i"))
        info("Visiting point {0}, {1}", RouteGuideUtil.getLatitude(point), RouteGuideUtil.getLongitude(point))
        requestObserver.onNext(point)
        Thread.sleep(rand.nextInt(1000) + 500)
        if (finishFuture.isDone) {
          done = true
        }
        i += 1
      }

      info(numMsg.toString)
      requestObserver.onCompleted
      finishFuture.get
      info("Finished RecordRoute")
    }
    catch {
      case e: Exception =>
        requestObserver.onError(e)
        RouteGuideClient.logger.log(Level.WARNING, "RecordRoute Failed", e)
        throw e
    }
  }

  def routeChat(): Unit = {
    info("*** RoutChat")
    val finishFuture = SettableFuture.create[Unit]
    val requestObserver = asyncStub.routeChat(new StreamObserver[RouteNote]() {
      def onNext(note: RouteNote) {
        info("Got message \"{0}\" at {1}, {2}", note.message, note.getLocation.latitude, note.getLocation.longitude)
      }

      def onError(t: Throwable) {
        finishFuture.setException(t)
      }

      def onCompleted() = {
        finishFuture.set(())
      }
    })
    try {
      val requests = Array(newNote("First message", 0, 0), newNote("Second message", 0, 1), newNote("Third message", 1, 0), newNote("Fourth message", 1, 1))
      for (request <- requests) {
        info("Sending message \"{0}\" at {1}, {2}", request.message, request.getLocation.latitude, request.getLocation.longitude)
        requestObserver.onNext(request)
      }
      requestObserver.onCompleted()
      finishFuture.get
      info("Finished RouteChat")
    }
    catch {
      case t: Exception =>
        requestObserver.onError(t)
        RouteGuideClient.logger.log(Level.WARNING, "RouteChat Failed", t)
        throw t
    }
  }

  private def newNote(message: String, lat: Int, lon: Int) = {
    RouteNote(message = message, location = Some(Point(latitude = lat, longitude = lon)))
  }
}
