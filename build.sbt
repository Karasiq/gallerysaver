
lazy val rootSettings = Seq(
  name := "gallerysaver",
  version := "1.0.0-M3",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.11.8",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= {
    val akkaV = "2.4.17"
    val akkaHttpV = "10.0.4"
    Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.github.karasiq" %% "commons" % "1.0.11",
      "com.github.karasiq" %% "commons-akka" % "1.0.11",
      "com.github.karasiq" %% "mapdbutils" % "1.1.1",
      "org.mapdb" % "mapdb" % "2.0-beta12",
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "net.codingwell" %% "scala-guice" % "4.1.0",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "net.sourceforge.htmlunit" % "htmlunit" % "2.35.0",
      "org.jsoup" % "jsoup" % "1.10.2",
      "org.jline" % "jline" % "3.12.1"
    )
  },
//  excludeDependencies ++= Seq(
//    "xalan" % "xalan",
//    "xerces" % "xercesImpl"
//  ),
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
  .settings(rootSettings/*, assemblySettings*/)
  .enablePlugins(JavaAppPackaging)
