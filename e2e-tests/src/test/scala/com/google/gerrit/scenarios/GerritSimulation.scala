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

import com.github.barbasa.gatling.git.GatlingGitConfiguration
import io.gatling.core.Predef._
import io.gatling.core.feeder.FileBasedFeederBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder

class GerritSimulation extends Simulation {
  implicit val conf: GatlingGitConfiguration = GatlingGitConfiguration()

  protected val name: String = this.getClass.getSimpleName
  protected val data: FileBasedFeederBuilder[Any]#F = jsonFile(s"data/$name.json").circular

  protected val httpRequest: HttpRequestBuilder = http(name).post("${url}")
  protected val httpProtocol: HttpProtocolBuilder = http.basicAuth(
    conf.httpConfiguration.userName,
    conf.httpConfiguration.password)
}
