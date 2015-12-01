package com.karasiq.gallerysaver.app.guice

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Singleton}
import com.karasiq.gallerysaver.app.guice.providers.{ActorSystemProvider, ExternalConfigProvider, GallerySaverMapDbProvider}
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

class GallerySaverMainModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[Config].toProvider[ExternalConfigProvider]
    bind[MapDbFile].toProvider[GallerySaverMapDbProvider].in[Singleton]
    bind[ActorSystem].toProvider[ActorSystemProvider].in[Singleton]
  }
}
