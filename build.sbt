name := "gallerysaver"

isSnapshot := true

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaV = "2.4.0"
  Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.github.karasiq" %% "commons" % "1.1-SNAPSHOT",
    "com.github.karasiq" %% "akka-commons" % "1.0",
    "com.github.karasiq" %% "mapdbutils" % "1.1-SNAPSHOT",
    "org.mapdb" % "mapdb" % "2.0-beta8",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "net.codingwell" %% "scala-guice" % "4.0.1",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "net.sourceforge.htmlunit" % "htmlunit" % "2.18"
  )
}

mainClass in Compile := Some("com.karasiq.gallerysaver.app.Main")

fork in run := true

fork in test := true