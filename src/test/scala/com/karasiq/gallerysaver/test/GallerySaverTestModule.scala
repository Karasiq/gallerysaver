package com.karasiq.gallerysaver.test

import java.nio.file.Files

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.karasiq.gallerysaver.app.guice.providers.ExternalConfigProvider
import com.karasiq.gallerysaver.mapdb.AppSQLContext
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule

class GallerySaverTestModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[Config].toProvider[ExternalConfigProvider]
    bind[AppSQLContext].toInstance(new AppSQLContext(ConfigFactory.parseString(
      s"""
        |path = "${Files.createTempDirectory("gallerysaver-test")}/gallerysaver-test"
        |init-script = "classpath:gallerysaver-h2-init.sql"
      """.stripMargin)))
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver-test"))
  }
}
