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

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration._

class CheckMasterBranchReplica1 extends ProjectSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue

  override def replaceOverride(in: String): String = {
    val next = replaceProperty("http_port1", 8081, in)
    super.replaceOverride(next)
  }

  private val httpForReplica = http.basicAuth(
    conf.httpConfiguration.userName,
    ConfigFactory.load().getString("http.password_replica"))

  private val createChange = new CreateChange
  private val approveChange = new ApproveChange(createChange)
  private val submitChange = new SubmitChange(createChange)
  private val getBranch = new GetMasterBranchRevision

  private val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(session => {
        session.set(getBranch.revisionKey, getBranch.revision.get)
      })
      .exec(http(uniqueName).get("${url}")
          .check(regex(getBranch.revisionPattern)
              .is(session => session(getBranch.revisionKey).as[String])))

  setUp(
    createChange.test.inject(
      nothingFor(stepWaitTime(createChange) seconds),
      atOnceUsers(single)
    ),
    approveChange.test.inject(
      nothingFor(stepWaitTime(approveChange) seconds),
      atOnceUsers(single)
    ),
    submitChange.test.inject(
      nothingFor(stepWaitTime(submitChange) seconds),
      atOnceUsers(single)
    ),
    getBranch.test.inject(
      nothingFor(stepWaitTime(getBranch) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ).protocols(httpForReplica),
  ).protocols(httpProtocol)
}
