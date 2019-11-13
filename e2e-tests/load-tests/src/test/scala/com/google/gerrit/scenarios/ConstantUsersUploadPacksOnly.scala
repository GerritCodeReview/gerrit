// Copyright (C) 2019 The Android Open Source Project
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

import com.github.barbasa.gatling.git.protocol.GitProtocol
import com.github.barbasa.gatling.git.request.builder.GitRequestBuilder
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import java.io._

import com.github.barbasa.gatling.git.{
  GatlingGitConfiguration,
  GitRequestSession
}
import org.apache.commons.io.FileUtils

import scala.concurrent.duration._

class ConstantUsersUploadPacksOnly extends Simulation {

  val concurrentUsers = Integer.getInteger("users", 5).toInt
  val durationMinutes = Integer.getInteger("durationMinutes", 5).toInt
  val description = System.getProperty("description", "")

  val gitProtocol = GitProtocol()
  implicit val conf = GatlingGitConfiguration()

  val feeder = jsonFile("data/requests-upload-packs-only.json").shuffle.circular

  val replayCallsScenario: ScenarioBuilder =
    scenario("Git clones constant users " + description)
      .forever {
        feed(feeder)
          .exec(
            new GitRequestBuilder(
              GitRequestSession("${cmd}", "${url}", "${ref-spec}")
            )
          )
      }

  setUp(replayCallsScenario.inject(atOnceUsers(concurrentUsers)))
    .protocols(gitProtocol)
    .maxDuration(durationMinutes minutes)

  after {
    try {
      //After is often called too early. Some retries should be implemented.
      Thread.sleep(5000)
      FileUtils.deleteDirectory(new File(conf.tmpBasePath))
    } catch {
      case e: IOException => {
        System.err.println(
          "Unable to delete temporary directory: " + conf.tmpBasePath
        )
        e.printStackTrace
      }
    }
  }
}
