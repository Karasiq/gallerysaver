package com.karasiq.gallerysaver.app.guice

import javax.inject.Named

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.dispatcher.GallerySaverDispatcher
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import com.karasiq.mapdb.MapDbFile

class GallerySaverDispatcherProvider @Inject() (actorSystem: ActorSystem, mapDbFile: MapDbFile, @Named("fileDownloader") fileDownloader: ActorRef, loaderRegistry: LoaderRegistry) extends Provider[ActorRef] {
  private val actor = actorSystem.actorOf(Props(classOf[GallerySaverDispatcher], mapDbFile, fileDownloader, loaderRegistry), "gallerySaverDispatcher")

  override def get(): ActorRef = {
    actor
  }
}
