package com.karasiq.gallerysaver.app.guice.providers

import javax.inject.Named
import java.nio.file.Paths

import scala.concurrent.ExecutionContext

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.typesafe.config.Config

import com.karasiq.gallerysaver.builtin.{ImageHostingLoader, PreviewLoader}
import com.karasiq.gallerysaver.dispatcher.{GallerySaverDispatcher, LoaderRegistry}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.mapdb.MapDbFile

class BaseGallerySaverContextProvider @Inject()(mapDbFile: MapDbFile, config: Config,
                                                actorSystem: ActorSystem, executionContext: ExecutionContext,
                                                @Named("fileDownloader") fileDownloader: ActorRef) extends Provider[GallerySaverContext] {

  def get(): GallerySaverContext = {
    val registry = LoaderRegistry()
    val gallerySaverDispatcher = actorSystem.actorOf(Props(classOf[GallerySaverDispatcher], Paths.get(config.getString("gallery-saver.destination")), mapDbFile, fileDownloader, registry), "gallerySaverDispatcher")

    implicit val context = GallerySaverContext(config, mapDbFile, executionContext, gallerySaverDispatcher, null, actorSystem, registry)

    context.registry
      .register(new PreviewLoader)
      .register(new ImageHostingLoader)

    context
  }
}