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

class FlushProjectsCache extends CacheFlushSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val default: String = name

  override def relativeRuntimeWeight = 2

  private val flushCache: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(httpRequest)

  private val createProject = new CreateProject(default)
  private val getCacheEntriesAfterProject = new GetProjectsCacheEntries(this)
  private val checkCacheEntriesAfterFlush = new CheckProjectsCacheFlushEntries(this)
  private val deleteProject = new DeleteProject(default)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(1)
    ),
    getCacheEntriesAfterProject.test.inject(
      nothingFor(stepWaitTime(getCacheEntriesAfterProject) seconds),
      atOnceUsers(1)
    ),
    flushCache.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(1)
    ),
    checkCacheEntriesAfterFlush.test.inject(
      nothingFor(stepWaitTime(checkCacheEntriesAfterFlush) seconds),
      atOnceUsers(1)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(1)
    ),
  ).protocols(httpProtocol)
}
