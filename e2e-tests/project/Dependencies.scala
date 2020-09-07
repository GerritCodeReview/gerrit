import sbt._

object Dependencies {
  val GatlingVersion = "3.2.0"
  val GatlingGitVersion = "1.0.12"

  lazy val gatling = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts",
    "io.gatling" % "gatling-test-framework",
  ).map(_ % GatlingVersion % Test)

  lazy val gatlingGit = Seq(
    "com.gerritforge" %% "gatling-git" % GatlingGitVersion excludeAll(
      ExclusionRule(organization = "io.gatling"),
      ExclusionRule(organization = "io.gatling.highcharts")
    )
  )
}
