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
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class CloneUsingBothProtocols extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).circular
  private val projectName = className
  private val duration = 2 * numberOfUsers

  override def replaceOverride(in: String): String = {
    replaceKeyWith("_project", projectName, in)
  }

  private val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(gitRequest)

  private val createProject = new CreateProject(projectName)
  private val deleteProject = new DeleteProject(projectName)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      constantUsersPerSec(numberOfUsers) during (duration seconds)
    ).protocols(gitProtocol),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) + duration seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
