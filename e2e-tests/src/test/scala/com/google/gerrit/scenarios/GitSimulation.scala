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

import java.io.{File, IOException}

import com.github.barbasa.gatling.git.GitRequestSession
import com.github.barbasa.gatling.git.protocol.GitProtocol
import com.github.barbasa.gatling.git.request.builder.GitRequestBuilder
import io.gatling.core.Predef._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.hooks.CommitMsgHook

class GitSimulation extends GerritSimulation {
  implicit val postMessageHook: Option[String] = Some(s"hooks/${CommitMsgHook.NAME}")

  protected val gitRequest = new GitRequestBuilder(GitRequestSession("${cmd}", "${url}"))
  protected val gitProtocol: GitProtocol = GitProtocol()

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
