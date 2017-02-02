name := "pws-akkastreamsutils"

version := "0.1-SNAPSHOT"
organization := "com.powerspace"

scalaVersion := "2.11.8"
scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-unchecked", "-Ywarn-dead-code", "-Ywarn-numeric-widen", "-Ywarn-unused", "-Ywarn-unused-import" /*,"-Ymacro-debug-lite"*/)

libraryDependencies ++= typesafeDependencies ++ kamonDependencies ++ akkaDependencies ++ testDependencies

val kamonVersion = "0.6.3"
lazy val kamonDependencies = Seq(
  "io.kamon" %% "kamon-core" % kamonVersion,
  "io.kamon" %% "kamon-scala" % kamonVersion,
  "io.kamon" %% "kamon-log-reporter" % kamonVersion,
  "io.kamon" %% "kamon-system-metrics" % kamonVersion,
  "io.kamon" %% "kamon-statsd" % kamonVersion
)

lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.4.2" % "test"
)

lazy val typesafeDependencies = Seq(
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
)

lazy val akkaVersion = "2.4.12"
lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.0.0")

testOptions in Test += Tests.Argument("-oD")

lazy val root = (project in file("."))
