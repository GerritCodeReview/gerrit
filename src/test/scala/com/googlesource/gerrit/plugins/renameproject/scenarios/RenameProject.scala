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

package com.googlesource.gerrit.plugins.renameproject.scenarios

import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import scala.concurrent.duration._

class RenameProject extends GerritSimulation {
  private var default: String = "GATLING_RENAME_TEST" + "_" + this.hashCode()
  private var projectRenamed: String = "GATLING_RENAME_TEST_RENAMED" + "_" + this.hashCode()
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue

  def this(default: String, projectRenamed: String) {
    this()
    this.default = default
    this.projectRenamed = projectRenamed
  }

  override def replaceOverride(in: String): String = {
    var next = replaceKeyWith("rename_var", projectRenamed, in)
    next = replaceKeyWith("project", default, next)
    next
  }

  private val createProject = new CreateProject(default)
  private val deleteProject = new DeleteProject(projectRenamed)

  private val test: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(httpRequest
          .body(ElFileBody(body)).asJson)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(1)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(1)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(1)
    )
  ).protocols(httpProtocol)
}
