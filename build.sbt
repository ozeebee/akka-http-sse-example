organization := "org.ozb"

name := "Akka HTTP SSE Example"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaVersion = "2.4.12"
  val akkaHttpVersion = "2.4.11"
  Seq(
    // Akka SSE
    "de.heikoseeberger" %% "akka-sse" % "1.11.0",
    // Akka HTTP
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaHttpVersion,
    // Akka HTTP JSON
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaHttpVersion,
    // Akka logging
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    // logging
    "org.slf4j" %  "slf4j-api" % "1.7.+",
    "ch.qos.logback" % "logback-classic" % "1.1.7"
  )
}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")
