package com.karasiq.gallerysaver.test

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.karasiq.gallerysaver.app.guice.providers.ExternalConfigProvider
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.mapdb.DBMaker

class GallerySaverTestModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[Config].toProvider[ExternalConfigProvider]
    bind[MapDbFile].toInstance(MapDbFile(DBMaker.memoryDB().transactionDisable().make()))
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver-test"))
  }
}
