// Copyright (C) 2021 The Android Open Source Project
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

import scala.collection.mutable
import scala.concurrent.duration._

class SubmitChangeInBranch extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).circular
  private var changesCopy: mutable.Queue[Int] = mutable.Queue[Int]()
  private val projectName = className

  override def relativeRuntimeWeight = 10

  private val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(session => {
        if (changesCopy.isEmpty) {
          changesCopy = createChange.numbers.clone()
        }
        session.set("number", changesCopy.dequeue())
      })
      .exec(http(uniqueName).post("${url}${number}/submit"))

  private val createProject = new CreateProject(projectName)
  private val createBranch = new CreateBranch(projectName)
  private val createChange = new CreateChange(projectName, createBranch)
  private val approveChange = new ApproveChange(createChange)
  private val deleteProject = new DeleteProject(projectName)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    createBranch.test.inject(
      nothingFor(stepWaitTime(createBranch) seconds),
      atOnceUsers(numberOfUsers)
    ),
    createChange.test.inject(
      nothingFor(stepWaitTime(createChange) seconds),
      atOnceUsers(numberOfUsers)
    ),
    approveChange.test.inject(
      nothingFor(stepWaitTime(approveChange) seconds),
      atOnceUsers(numberOfUsers)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(numberOfUsers)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
