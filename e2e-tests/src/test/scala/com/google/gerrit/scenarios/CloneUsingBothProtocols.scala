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

import io.gatling.core.Predef._
import io.gatling.core.feeder.FileBasedFeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class CloneUsingBothProtocols extends GitSimulation {
  private val data: FileBasedFeederBuilder[Any]#F = jsonFile(resource).queue

  private val test: ScenarioBuilder = scenario(name)
      .feed(data)
      .exec(gitRequest)

  private val createProject = new CreateProject
  private val deleteProject = new DeleteProject

  setUp(
    createProject.test.inject(
      atOnceUsers(1)
    ),
    test.inject(
      nothingFor(1 second),
      constantUsersPerSec(1) during (2 seconds)
    ),
    deleteProject.test.inject(
      nothingFor(3 second),
      atOnceUsers(1)
    ),
  ).protocols(gitProtocol, httpProtocol)
}
