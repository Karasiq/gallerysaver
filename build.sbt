lazy val rootSettings = Seq(
  name := "gallerysaver",
  isSnapshot := true,
  version := "1.0.0-M2-SNAPSHOT",
  scalaVersion := "2.11.7",
  libraryDependencies ++= {
    val akkaV = "2.4.2"
    Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.github.karasiq" %% "commons" % "1.0.3",
      "com.github.karasiq" %% "commons-akka" % "1.0.3",
      "com.github.karasiq" %% "mapdbutils" % "1.1.0",
      "org.mapdb" % "mapdb" % "2.0-beta12",
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
      "net.codingwell" %% "scala-guice" % "4.0.1",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test",
      "net.sourceforge.htmlunit" % "htmlunit" % "2.19",
      "org.jsoup" % "jsoup" % "1.8.3"
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