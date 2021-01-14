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

  private val packageName = getClass.getPackage.getName
  private val path = packageName.replaceAllLiterally(".", "/")

  protected val className: String = getClass.getSimpleName
  private val pathName = s"data/$path/$className"
  protected val resource = s"$pathName.json"
  protected val body = s"$pathName-body.json"

  protected val uniqueName: String = className + "-" + hashCode()
  protected val single = 1

  val numberOfUsers: Int = replaceProperty("number_of_users", single).toInt
  val replicationDelay: Int = replaceProperty("replication_delay", 15).toInt
  private val powerFactor = replaceProperty("power_factor", 1.0).toDouble
  protected val SecondsPerWeightUnit = 2
  val maxExecutionTime: Int = (SecondsPerWeightUnit * relativeRuntimeWeight * powerFactor).toInt
  private var cumulativeWaitTime = 0

  /**
   * How long a scenario step should wait before starting to execute.
   * This is also registering that step's resulting wait time, so that time
   * can be reused cumulatively by a potentially following scenario step.
   * (Otherwise, the Gatling set-up scenario steps execute all at once.)
   *
   * @param scenario for which to return a wait time.
   * @return that step's wait time as an Int.
   */
  protected def stepWaitTime(scenario: GerritSimulation): Int = {
    val currentWaitTime = cumulativeWaitTime
    cumulativeWaitTime += scenario.maxExecutionTime
    currentWaitTime
  }

  protected val httpRequest: HttpRequestBuilder = http(uniqueName).post("${url}")
  protected val httpProtocol: HttpProtocolBuilder = http.basicAuth(
    conf.httpConfiguration.userName,
    conf.httpConfiguration.password)

  protected val keys: PartialFunction[(String, Any), Any] = {
    case ("entries", entries) =>
      replaceProperty("projects_entries", "1", entries.toString)
    case ("number", number) =>
      val precedes = replaceKeyWith("_number", 0, number.toString)
      replaceProperty("number", 1, precedes)
    case ("parent", parent) =>
      replaceProperty("parent", "All-Projects", parent.toString)
    case ("project", project) =>
      var precedes = replaceKeyWith("_project", className, project.toString)
      precedes = replaceOverride(precedes)
      replaceProperty("project", precedes)
    case ("url", url) =>
      var in = replaceOverride(url.toString)
      in = replaceProperty("hostname", "localhost", in)
      in = replaceProperty("http_port", 8080, in)
      in = replaceProperty("http_scheme", "http", in)
      replaceProperty("ssh_port", 29418, in)
  }

  private def replaceProperty(term: String, in: String): String = {
    replaceProperty(term, term, in)
  }

  private def replaceProperty(term: String, default: Any): String = {
    replaceProperty(term, default, term.toUpperCase)
  }

  protected def replaceProperty(term: String, default: Any, in: String): String = {
    val property = packageName + "." + term
    var value = default
    default match {
      case _: String | _: Double =>
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
   * This is usually similar to how [[keys]] is implemented above.
   *
   * <pre>
   * override def replaceOverride(in: String): String = {
   * // Simple e.g., replaceProperty("EXTENSION_JSON_KEY", "default", in)
   * </pre>
   *
   * @param in which string to perform the replacements.
   * @return the resulting String.
   */
  def replaceOverride(in: String): String = {
    in
  }

  /**
   * Meant to be optionally overridden by (heavier) scenarios.
   * This is the relative runtime weight of the scenario class or type,
   * compared to other scenarios' own runtime weights.
   *
   * The default weight or unit of weight is the pre-assigned value below.
   * This default applies to any scenario class that is not overriding it
   * with a greater, relative runtime weight value. Overriding scenarios
   * happen to relatively require more run time than siblings, prior to
   * being expected as completed.
   *
   * @return the relative runtime weight of this scenario as an Int.
   */
  def relativeRuntimeWeight = 1
}
