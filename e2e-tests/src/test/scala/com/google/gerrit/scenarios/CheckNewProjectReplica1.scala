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

class CheckNewProjectReplica1 extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val default: String = name
  private lazy val replicationDuration = replicationDelay + SecondsPerWeightUnit

  override def relativeRuntimeWeight: Int = replicationDuration / SecondsPerWeightUnit

  override def replaceOverride(in: String): String = {
    var next = replaceProperty("http_port1", 8081, in)
    next = replaceKeyWith("_project", default, next)
    super.replaceOverride(next)
  }

  private val test: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(gitRequest)

  private val createProject = new CreateProject(default)
  private val deleteProject = new DeleteProject(default)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) + replicationDuration seconds),
      atOnceUsers(single)
    ).protocols(gitProtocol),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
