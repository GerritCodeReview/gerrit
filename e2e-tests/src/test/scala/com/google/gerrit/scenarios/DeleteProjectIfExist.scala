package com.google.gerrit.scenarios

import io.gatling.core.Predef.{atOnceUsers, exec, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._


class DeleteProjectIfExist extends ProjectSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue

  def this(projectName: String) {
    this()
    this.projectName = projectName
  }

  val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(
        http(uniqueName)
            .get("${url}")
            .check(
              status.in(200, 404),
              status.transform(status => 200.equals(status))
                  .saveAs("FOUND")))
      .doIf(session => session.attributes("FOUND").equals(true)) {
        exec(http(uniqueName).post("${url}/delete-project~delete").body(ElFileBody(body)).asJson)
      }


  setUp(
    test.inject(
      atOnceUsers(single)
    )).protocols(httpProtocol)
}
