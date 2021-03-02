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

import io.gatling.core.Predef.{atOnceUsers, exec, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._

class DeleteProjectIfExists extends ProjectSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val statusKey = "status"

  private val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(http(uniqueName)
            .get("${url}")
            .check(status.saveAs(statusKey)))
      .doIf(session => session.attributes(statusKey).equals(200)) {
        exec(http(uniqueName)
            .post("${url}/delete-project~delete")
            .body(ElFileBody(body)).asJson)
      }

  setUp(
    test.inject(
      atOnceUsers(single)
    )).protocols(httpProtocol)
}
