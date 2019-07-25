
lazy val rootSettings = Seq(
  name := "gallerysaver",
  version := "1.0.0",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.8",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= {
    val akkaV = "2.5.23"
    val akkaHttpV = "10.1.9"
    Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.github.karasiq" %% "commons" % "1.0.11",
      "com.github.karasiq" %% "commons-akka" % "1.0.11",
      "com.h2database" % "h2" % "1.4.192",
      "io.getquill" %% "quill-jdbc" % "1.2.1",
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "net.codingwell" %% "scala-guice" % "4.1.0",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "net.sourceforge.htmlunit" % "htmlunit" % "2.35.0",
      "org.jsoup" % "jsoup" % "1.10.2",
      "org.jline" % "jline" % "3.12.1",
      "org.slf4j" % "slf4j-api" % "1.7.5",
      "org.slf4j" % "slf4j-log4j12" % "1.7.5"
    )
  },
  mainClass in Compile := Some("com.karasiq.gallerysaver.app.Main"),
  fork in run := true,
  fork in test := true,
  connectInput in run := true
)

lazy val assemblySettings = Seq(
  mainClass in assembly := (mainClass in Compile).value,
  test in assembly := {},
  assemblyJarName in assembly := "gallerysaver.jar",
  mappings in Universal := {
    val universalMappings = (mappings in Universal).value
    val fatJar = (assembly in Compile).value
    val filtered = universalMappings.filterNot(_._2.endsWith(".jar"))
    filtered :+ (fatJar → ("lib/" + fatJar.getName))
  },
  scriptClasspath := Seq((assemblyJarName in assembly).value)
)

lazy val root = Project("gallerysaver", file("."))
  .settings(rootSettings, assemblySettings)
  .enablePlugins(JavaAppPackaging)
