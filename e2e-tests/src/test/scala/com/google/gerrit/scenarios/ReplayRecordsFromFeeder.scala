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
  private val data: FileBasedFeederBuilder[Any]#F = jsonFile(resource).circular

  private val test: ScenarioBuilder = scenario(name)
      .repeat(10000) {
        feed(data)
            .exec(gitRequest)
      }

  setUp(
    test.inject(
      nothingFor(4 seconds),
      atOnceUsers(10),
      rampUsers(10) during (5 seconds),
      constantUsersPerSec(20) during (15 seconds),
      constantUsersPerSec(20) during (15 seconds) randomized
    )).protocols(gitProtocol)
      .maxDuration(60 seconds)
}
