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

import java.net.URL
import javax.json.{Json, JsonObject}

import io.grpc.examples.routeguide.route_guide.{Feature, Point}

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.13.2/examples/src/main/java/io/grpc/examples/routeguide/RouteGuideUtil.java]]
 */
object RouteGuideUtil {
  private val COORD_FACTOR: Double = 1e7

  def getLatitude(location: Point): Double = {
    location.latitude / COORD_FACTOR
  }

  def getLongitude(location: Point): Double = {
    location.longitude / COORD_FACTOR
  }

  def getDefaultFeaturesFile: URL = {
    classOf[RouteGuideClient].getResource("route_guide_db.json")
  }

  def parseFeatures(file: URL): List[Feature] = {
    val input = file.openStream
    try {
      val reader = Json.createReader(input)
      import scala.collection.convert.decorateAsScala._
      reader.readArray.asScala.map { value =>
        val obj = value.asInstanceOf[JsonObject]
        val name = obj.getString("name", "")
        val location = obj.getJsonObject("location")
        val lat = location.getInt("latitude")
        val lon = location.getInt("longitude")
        Feature(name = name, location = Some(Point(latitude = lat, longitude = lon)))
      }(collection.breakOut)
    } finally {
      input.close()
    }
  }

  def exists(feature: Feature): Boolean = {
    feature != null && feature.name.nonEmpty
  }
}

