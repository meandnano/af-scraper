name := "scr-af"

version := "0.1"

scalaVersion := "2.13.3"

lazy val akkaVersion = "2.6.9"
libraryDependencies ++= Seq(
  "org.tomlj" % "tomlj" % "1.0.0",
  "org.reactivemongo" %% "reactivemongo" % "1.0.0-noshaded",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "com.typesafe.akka" %% "akka-http-core" % "10.2.0",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % "2.0.2",

  "org.scalatest" %% "scalatest" % "3.2.0" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.mockito" % "mockito-core" % "3.5.13" % Test,
)
