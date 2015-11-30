package com.karasiq.gallerysaver.app.guice

import java.nio.file.Paths

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.karasiq.fileutils.PathUtils._
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule

class GallerySaverMainModule extends AbstractModule with ScalaModule {
  private val config = {
    val defaultConfig = ConfigFactory.load()
    val fileName = defaultConfig.getString("gallery-saver.external-config")

    Paths.get(fileName) match {
      case file if file.isRegularFile ⇒
        ConfigFactory.parseFile(file.toFile)
          .withFallback(defaultConfig)
          .resolve()

      case _ ⇒
        defaultConfig
    }
  }

  override def configure(): Unit = {
    bind[Config].toInstance(config)
    bind[MapDbFile].toProvider[GallerySaverMapDbProvider]
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver", config))
  }
}
