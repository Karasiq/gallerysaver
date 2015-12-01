package com.karasiq.gallerysaver.app.guice.providers

import java.nio.file.Paths
import javax.inject.Named

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.dispatcher.GallerySaverDispatcher
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config

class GallerySaverDispatcherProvider @Inject() (config: Config, actorSystem: ActorSystem, mapDbFile: MapDbFile, @Named("fileDownloader") fileDownloader: ActorRef, loaderRegistry: LoaderRegistry) extends Provider[ActorRef] {
  private def props(): Props = {
    Props(classOf[GallerySaverDispatcher], Paths.get(config.getString("gallery-saver.destination")), mapDbFile, fileDownloader, loaderRegistry)
  }

  override def get(): ActorRef = {
    actorSystem.actorOf(this.props(), "gallerySaverDispatcher")
  }
}
