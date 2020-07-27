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

class FlushProjectsCacheThenRebuild extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue

  private val test: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(httpRequest)

  private val checkCacheEntriesAfterFlush = new CheckProjectsCacheFlushEntries
  private val rebuildCache = new ListProjects

  setUp(
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    checkCacheEntriesAfterFlush.test.inject(
      nothingFor(stepWaitTime(checkCacheEntriesAfterFlush) seconds),
      atOnceUsers(single)
    ),
    rebuildCache.test.inject(
      nothingFor(stepWaitTime(rebuildCache) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
