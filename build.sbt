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
      "net.sourceforge.htmlunit" % "htmlunit" % "2.25",
      "org.jsoup" % "jsoup" % "1.10.2"
    )
  },
  mainClass in Compile := Some("com.karasiq.gallerysaver.app.Main"),
  fork in run := true,
  fork in test := true,
  connectInput in run := true
)

lazy val root = Project("gallerysaver", file("."))
  .settings(rootSettings)
  .enablePlugins(JavaAppPackaging)