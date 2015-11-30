package com.karasiq.gallerysaver.app.guice

import javax.inject.Named

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.dispatcher.GallerySaverDispatcher
import com.karasiq.gallerysaver.scripting.LoaderRegistry

class GallerySaverDispatcherProvider @Inject() (@Named("fileDownloader") fileDownloader: ActorRef, actorSystem: ActorSystem, loaderRegistry: LoaderRegistry) extends Provider[ActorRef] {
  private val actor = actorSystem.actorOf(Props(classOf[GallerySaverDispatcher], fileDownloader, loaderRegistry), "gallerySaverDispatcher")

  override def get(): ActorRef = {
    actor
  }
}
