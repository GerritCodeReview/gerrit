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

import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class SubmitChange extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val default = className
  private var createChange = new CreateChange(default)

  override def relativeRuntimeWeight = 10

  def this(createChange: CreateChange) {
    this()
    this.createChange = createChange
  }

  val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(session => {
        session.set("number", createChange.number)
      })
      .exec(http(uniqueName).post("${url}${number}/submit"))

  private val createProject = new CreateProject(default)
  private val approveChange = new ApproveChange(createChange)
  private val deleteProject = new DeleteProject(default)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    createChange.test.inject(
      nothingFor(stepWaitTime(createChange) seconds),
      atOnceUsers(single)
    ),
    approveChange.test.inject(
      nothingFor(stepWaitTime(approveChange) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
