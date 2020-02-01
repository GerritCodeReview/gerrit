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

import java.io._

import com.github.barbasa.gatling.git.protocol.GitProtocol
import com.github.barbasa.gatling.git.request.builder.GitRequestBuilder
import com.github.barbasa.gatling.git.{GatlingGitConfiguration, GitRequestSession}
import io.gatling.core.Predef._
import io.gatling.core.feeder.FileBasedFeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.hooks._

import scala.concurrent.duration._

class CloneUsingBothProtocols extends Simulation {

  implicit val conf: GatlingGitConfiguration = GatlingGitConfiguration()
  implicit val postMessageHook: Option[String] = Some(s"hooks/${CommitMsgHook.NAME}")

  private val name: String = this.getClass.getSimpleName
  private val file = "data/" + name + ".json"
  private val data: FileBasedFeederBuilder[Any]#F = jsonFile(file).circular
  private val request = new GitRequestBuilder(GitRequestSession("${cmd}", "${url}"))
  private val protocol: GitProtocol = GitProtocol()

  private val test: ScenarioBuilder = scenario(name)
      .feed(data)
      .exec(request)

  setUp(
    test.inject(
      constantUsersPerSec(1) during (2 seconds))
  ).protocols(protocol)

  after {
    Thread.sleep(5000)
    val path = conf.tmpBasePath
    try {
      FileUtils.deleteDirectory(new File(path))
    } catch {
      case e: IOException =>
        System.err.println("Unable to delete temporary directory " + path)
        e.printStackTrace()
    }
  }
}
