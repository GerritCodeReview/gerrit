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

import io.gatling.core.Predef._
import io.gatling.core.feeder.FileBasedFeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class ReplayRecordsFromFeeder extends GitSimulation {
  private val data: FileBasedFeederBuilder[Any]#F#F = jsonFile(resource).convert(url).circular
  private val default: String = name

  override def replaceOverride(in: String): String = {
    replaceKeyWith("_project", default, in)
  }

  private val test: ScenarioBuilder = scenario(name)
      .repeat(10) {
        feed(data)
            .exec(gitRequest)
      }

  private val createProject = new CreateProject(default)
  private val deleteProject = new DeleteProject(default)

  setUp(
    createProject.test.inject(
      atOnceUsers(1)
    ),
    test.inject(
      nothingFor(4 seconds),
      atOnceUsers(10),
      rampUsers(10) during (5 seconds),
      constantUsersPerSec(20) during (15 seconds),
      constantUsersPerSec(20) during (15 seconds) randomized
    ),
    deleteProject.test.inject(
      nothingFor(59 seconds),
      atOnceUsers(1)
    ),
  ).protocols(gitProtocol, httpProtocol)
      .maxDuration(61 seconds)
}
