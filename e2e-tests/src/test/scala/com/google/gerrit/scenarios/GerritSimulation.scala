// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.scenarios

import com.github.barbasa.gatling.git.GatlingGitConfiguration
import io.gatling.core.Predef._
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder

class GerritSimulation extends Simulation {
  implicit val conf: GatlingGitConfiguration = GatlingGitConfiguration()

  private val pack: String = this.getClass.getPackage.getName
  private val path: String = pack.replaceAllLiterally(".", "/")
  protected val name: String = this.getClass.getSimpleName
  protected val resource: String = s"data/$path/$name.json"

  protected val httpRequest: HttpRequestBuilder = http(name).post("${url}")
  protected val httpProtocol: HttpProtocolBuilder = http.basicAuth(
    conf.httpConfiguration.userName,
    conf.httpConfiguration.password)

  protected val url: PartialFunction[(String, Any), Any] = {
    case ("url", url) =>
      var in = replaceOverride(url.toString)
      in = replaceProperty("hostname", "localhost", in)
      in = replaceProperty("http_port", 8080, in)
      replaceProperty("ssh_port", 29418, in)
  }

  protected def replaceProperty(term: String, default: Any, in: String): String = {
    val property = pack + "." + term
    var value = default
    default match {
      case _: String =>
        val propertyValue = Option(System.getProperty(property))
        if (propertyValue.nonEmpty) {
          value = propertyValue.get
        }
      case _: Integer =>
        value = Integer.getInteger(property, default.asInstanceOf[Integer])
    }
    replaceKeyWith(term, value, in)
  }

  protected def replaceKeyWith(term: String, value: Any, in: String): String = {
    val key: String = term.toUpperCase
    in.replaceAllLiterally(key, value.toString)
  }

  /**
   * Meant to be optionally overridden by plugins or other extensions.
   * Such potential overriding methods, such as the example below,
   * typically return resulting call(s) to [[replaceProperty()]].
   * This is usually similar to how [[url]] is implemented above.
   *
   * <pre>
   * override def replaceOverride(in: String): String = {
   * // Simple e.g., replaceProperty("EXTENSION_JSON_KEY", "default", in)
   * </pre>
   */
  def replaceOverride(in: String): String = {
    in
  }
}
