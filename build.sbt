enablePlugins(JavaAppPackaging)
lazy val distProject = project
  .in(file("."))
  .enablePlugins(JavaAgent, JavaAppPackaging)
  .settings(
//    javaAgents += "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "1.7.2"
    javaAgents += JavaAgent("OpenTelemetry" % "javaagent" % "1.7.2-Release" % "runtime")
//      javaAgents += JavaAgent("com.example" % "agent" % "1.2.3" % "compile;test", arguments = "java_agent_argument_string")

  )


//bashScriptExtraDefines += """addJava" -javaagent:${app_home}/opentelemetry-javaagent-all.jar"""

resolvers += "Artifactory" at "https://aspecto.jfrog.io/artifactory/aspecto-public-maven"

name := "akka-http-microservice"
organization := "com.theiterators"
version := "1.0"
scalaVersion := "3.1.0"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")
//resolvers

//javaAgents += "com.example" % "agent" % "1.2.3"
//javaAgents += JavaAgent("io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "1.8.0" % "runtime")



envVars := Map(
  "OTEL_JAVAAGENT_DEBUG" -> "true",

  "OTEL_TRACES_EXPORTER" -> "otlp",
  "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT" -> "https://otelcol.aspecto.io:4317",
  "OTEL_SERVICE_NAME" -> "scala-test-michael",
//  "OTEL_EXPORTER_OTLP_HEADERS" -> "1111",

)


libraryDependencies ++= {
  val akkaHttpV      = "10.1.8"
  val akkaV          = "2.6.17"
  val scalaTestV     = "3.2.10"
  val circeV         = "0.14.1"
  val akkaHttpCirceV = "1.38.2"
  val otelVersion    = "1.9.0"
  val otelVersionAlpha    = "1.9.0-alpha"
  val otelInstrumentAlpha    = "1.8.0-alpha"
  val otelInstrumentAlpha2    = "1.9.0-alpha"


  Seq(
    "io.circe"          %% "circe-core" % circeV,
    "io.circe"          %% "circe-parser" % circeV,
    "io.circe"          %% "circe-generic" % circeV,
    "org.scalatest"     %% "scalatest" % scalaTestV % "test",

  //    "OpenTelemetry" % "javaagent" % "1.7.2-Release",
//    "io.opentelemetry" % "opentelemetry-exporter-otlp" % otelVersion,
    "io.opentelemetry" % "opentelemetry-sdk" % "1.9.0",
    "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.9.0",
//    "io.opentelemetry" % "opentelemetry-exporter-jaeger" % otelVersion,
//    "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % otelVersionAlpha,
//    "io.opentelemetry" % "opentelemetry-sdk-extension-tracing-incubator" % otelInstrumentAlpha2,
//    "io.opentelemetry.javaagent.instrumentation" % "opentelemetry-javaagent-akka-http-10.0" % otelInstrumentAlpha,
//    "io.opentelemetry.javaagent.instrumentation" % "opentelemetry-javaagent-akka-actor-2.5" % otelInstrumentAlpha,
//    "io.opentelemetry.javaagent.instrumentation" % "opentelemetry-javaagent-scala-executors" % otelInstrumentAlpha,
//
//    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api" % otelInstrumentAlpha
  ) ++ Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,

    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
  ).map(_.cross(CrossVersion.for3Use2_13))

}




Revolver.settings
