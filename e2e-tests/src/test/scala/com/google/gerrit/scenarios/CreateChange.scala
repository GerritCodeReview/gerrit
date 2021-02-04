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
import io.gatling.http.Predef._

import scala.collection.mutable
import scala.concurrent.duration._

class CreateChange extends ProjectSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).circular
  private val numberKey = "_number"
  private val weightPerUser = 0.1
  private var createBranch: Option[CreateBranch] = None
  private var branchesCopy: mutable.Queue[String] = mutable.Queue[String]()
  var number = 0
  var numbers: mutable.Queue[Int] = mutable.Queue[Int]()

  override def relativeRuntimeWeight: Int = 2 + (numberOfUsers * weightPerUser).toInt

  def this(projectName: String) {
    this()
    this.projectName = projectName
  }

  def this(projectName: String, createBranch: CreateBranch) {
    this()
    this.projectName = projectName
    this.createBranch = Some(createBranch)
  }

  val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(session => {
        var branchId = "master"
        if (createBranch.nonEmpty) {
          if (branchesCopy.isEmpty) {
            branchesCopy = createBranch.get.branches.clone()
          }
          branchId = branchesCopy.dequeue()
        }
        session.set("branch", branchId)
      })
      .exec(httpRequest
          .body(ElFileBody(body)).asJson
          .check(regex("\"" + numberKey + "\":(\\d+),").saveAs(numberKey)))
      .exec(session => {
        number = session(numberKey).as[Int]
        numbers += number
        session
      })

  private val createProject = new CreateProject(projectName)
  private val deleteProject = new DeleteProject(projectName)
  private val deleteChange = new DeleteChange(this)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(numberOfUsers)
    ),
    deleteChange.test.inject(
      nothingFor(stepWaitTime(deleteChange) seconds),
      atOnceUsers(numberOfUsers)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
