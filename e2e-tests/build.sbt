import Dependencies._

enablePlugins(GatlingPlugin)

lazy val root = (project in file("."))
    .settings(
      inThisBuild(List(
        organization := "com.google.gerrit",
        scalaVersion := "2.12.8",
        version := "0.1.0-SNAPSHOT"
      )),
      name := "gerrit",
      libraryDependencies ++=
          gatling ++ gatlingGit ++
              Seq("io.gatling" % "gatling-core" % GatlingVersion) ++
              Seq("io.gatling" % "gatling-app" % GatlingVersion),
      scalacOptions += "-language:postfixOps"
    )
