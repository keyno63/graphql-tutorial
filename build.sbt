name := "graphql-tutorial"

version := appVersion

scalaVersion := scalaBaseVersion

val scalaBaseVersion = "2.12.11"
val appVersion = "0.1"

lazy val sangriaVersion = "2.0.0"
lazy val sangriaSlowlogVersion = "0.1.8"
lazy val sangriaCirceVersion = "1.2.1"

val calibanVersion = "0.9.0"

lazy val circeVersion = "0.12.1"
lazy val circeOptVersion = "0.9.3"
lazy val circePlayVersion = "2812.0"

lazy val commonSettings = Seq(
  scalaVersion := scalaBaseVersion,
  version := appVersion,
  libraryDependencies ++= Seq(
    // test
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",

    // akka
    "com.typesafe.akka" %% "akka-actor" % "2.6.0-M5",
    "com.typesafe.akka" %% "akka-testkit" % "2.6.0-M5" % Test,
  ),
)

lazy val `sangria` = (project in file("sangria"))
  .enablePlugins(PlayScala)
  .settings(commonSettings)
  .settings(
    // copy from https://github.com/keyno63/sangria-play-sample
    libraryDependencies ++=
      Seq( jdbc , ehcache , ws , specs2 % Test , guice ) ++
      Seq(
        // sangria
        "org.sangria-graphql" %% "sangria" % sangriaVersion,
        "org.sangria-graphql" %% "sangria-slowlog" % sangriaSlowlogVersion,
        "org.sangria-graphql" %% "sangria-circe" % sangriaCirceVersion
      ) ++
      Seq(
        // circe(json libs)
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-parser",
        "io.circe" %% "circe-generic"
      ).map(_ % circeVersion) ++
      Seq(
        "io.circe" %% "circe-optics" % circeOptVersion,
        "com.dripower" %% "play-circe" % circePlayVersion
      )
  )

lazy val `caliban` = (project in file("caliban"))
  .settings(commonSettings)
  .settings(
    name := "caliban",
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.github.ghostdogpr" %% "caliban",
      "com.github.ghostdogpr" %% "caliban-akka-http"
    ).map(_ % calibanVersion) ++
      Seq(
        "de.heikoseeberger" %% "akka-http-circe"  % "1.32.0",
        "io.circe"          %% "circe-generic"    % circeVersion
      )
  )