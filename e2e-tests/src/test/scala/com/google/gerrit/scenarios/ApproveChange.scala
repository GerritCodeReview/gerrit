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

class ApproveChange extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private var createChange: Option[CreateChange] = None

  def this(createChange: CreateChange) {
    this()
    this.createChange = Some(createChange)
  }

  val test: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(session => {
        if (createChange.nonEmpty) {
          session.set("number", createChange.get.number)
        } else {
          session
        }
      })
      .exec(http(unique)
          .post("${url}${number}/revisions/current/review")
          .body(ElFileBody(body)).asJson)

  setUp(
    test.inject(
      atOnceUsers(1)
    )).protocols(httpProtocol)
}
