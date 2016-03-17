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

import java.lang.Math.{atan2, cos, max, min, sin, sqrt, toRadians}
import java.net.URL
import java.util.Collections
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.logging.{Level, Logger}

import io.grpc.{Server, ServerBuilder}
import io.grpc.examples.routeguide.route_guide._
import io.grpc.stub.StreamObserver

import scala.collection.convert.decorateAll._
import scala.concurrent.{ExecutionContext, Future}

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.13.2/examples/src/main/java/io/grpc/examples/routeguide/RouteGuideServer.java]]
 */
object RouteGuideServer {
  private val logger: Logger = Logger.getLogger(classOf[RouteGuideServer].getName)

  def main(args: Array[String]) {
    val server = new RouteGuideServer(8980)
    server.start(ExecutionContext.global)
    server.blockUntilShutdown()
  }

  private object RouteGuideService {
    private def calcDistance(start: Point, end: Point): Double = {
      val lat1 = RouteGuideUtil.getLatitude(start)
      val lat2 = RouteGuideUtil.getLatitude(end)
      val lon1 = RouteGuideUtil.getLongitude(start)
      val lon2 = RouteGuideUtil.getLongitude(end)
      val r = 6371000
      val φ1 = toRadians(lat1)
      val φ2 = toRadians(lat2)
      val Δφ = toRadians(lat2 - lat1)
      val Δλ = toRadians(lon2 - lon1)
      val a = sin(Δφ / 2) * sin(Δφ / 2) + cos(φ1) * cos(φ2) * sin(Δλ / 2) * sin(Δλ / 2)
      val c = 2 * atan2(sqrt(a), sqrt(1 - a))
      r * c
    }
  }

  private class RouteGuideService(features: Seq[Feature]) extends RouteGuideGrpc.RouteGuide {
    private final val routeNotes: ConcurrentMap[Point, java.util.List[RouteNote]] =
      new ConcurrentHashMap[Point, java.util.List[RouteNote]]

    override def getFeature(request: Point) = Future.successful(checkFeature(request))

    def listFeatures(request: Rectangle, responseObserver: StreamObserver[Feature]) {
      val lo = request.getLo
      val hi = request.getHi
      val left = min(lo.longitude, hi.longitude)
      val right = max(lo.longitude, hi.longitude)
      val top = max(lo.latitude, hi.latitude)
      val bottom = min(lo.latitude, hi.latitude)
      for (feature <- features) {
        if (RouteGuideUtil.exists(feature)) {
          val location = feature.getLocation
          val lat = location.latitude
          val lon = location.longitude
          if (lon >= left && lon <= right && lat >= bottom && lat <= top) {
            responseObserver.onNext(feature)
          }
        }
      }
      responseObserver.onCompleted()
    }

    def recordRoute(responseObserver: StreamObserver[RouteSummary]): StreamObserver[Point] = {
      new StreamObserver[Point]() {
        private[this] var pointCount = 0
        private[this] var featureCount = 0
        private[this] var distance = 0
        private[this] var previous: Point = null
        private[this] final val startTime: Long = System.nanoTime

        def onNext(point: Point) {
          pointCount += 1
          if (RouteGuideUtil.exists(checkFeature(point))) {
            featureCount += 1
          }
          if (previous != null) {
            distance += RouteGuideService.calcDistance(previous, point).toInt
          }
          previous = point
        }

        def onError(t: Throwable) {
          logger.log(Level.WARNING, "recordRoute cancelled", t)
        }

        def onCompleted(): Unit = {
          val seconds = NANOSECONDS.toSeconds(System.nanoTime - startTime)
          responseObserver.onNext(RouteSummary(pointCount = pointCount, featureCount = featureCount, distance = distance, elapsedTime = seconds.toInt))
          responseObserver.onCompleted()
        }
      }
    }

    def routeChat(responseObserver: StreamObserver[RouteNote]): StreamObserver[RouteNote] = {
      new StreamObserver[RouteNote]() {
        def onNext(note: RouteNote) {
          val notes = getOrCreateNotes(note.getLocation)
          for (prevNote <- notes.asScala) {
            responseObserver.onNext(prevNote)
          }
          notes.add(note)
        }

        def onError(t: Throwable) {
          logger.log(Level.WARNING, "routeChat cancelled", t)
        }

        def onCompleted() =  {
          responseObserver.onCompleted()
        }
      }
    }

    private def getOrCreateNotes(location: Point): java.util.List[RouteNote] = {
      val notes = Collections.synchronizedList(new java.util.ArrayList[RouteNote])
      val prevNotes = routeNotes.putIfAbsent(location, notes)
      if (prevNotes != null) prevNotes else notes
    }

    private def checkFeature(location: Point): Feature = {
      features.find{ feature =>
        feature.getLocation.latitude == location.latitude && feature.getLocation.longitude == location.longitude
      }.getOrElse{
        Feature(name = "", location = Option(location))
      }
    }

  }

}

class RouteGuideServer(port: Int, features: Seq[Feature]) { self =>
  private[this] var server: Server = null

  def this(port: Int, featureFile: URL) {
    this(port, RouteGuideUtil.parseFeatures(featureFile))
  }

  def this(port: Int) {
    this(port, RouteGuideUtil.getDefaultFeaturesFile)
  }

  def start(executionContext: ExecutionContext): Unit = {
    server = ServerBuilder.forPort(port).addService(RouteGuideGrpc.bindService(new RouteGuideServer.RouteGuideService(features), executionContext)).build.start
    RouteGuideServer.logger.info("Server started, listening on " + port)
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        Console.err.println("*** shutting down gRPC server since JVM is shutting down")
        self.stop()
        Console.err.println("*** server shut down")
      }
    })
  }

  def stop(): Unit = {
    if (server != null) {
      server.shutdown
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }
}
