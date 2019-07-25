package com.karasiq.gallerysaver.app.guice.providers

import java.nio.file.Paths

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.builtin.{ImageHostingLoader, PreviewLoader}
import com.karasiq.gallerysaver.dispatcher.{GallerySaverDispatcher, LoaderRegistry}
import com.karasiq.gallerysaver.mapdb.{AppSQLContext, GalleryCacheStore}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.typesafe.config.Config
import javax.inject.Named

import scala.concurrent.ExecutionContext

class BaseGallerySaverContextProvider @Inject()(sqlContext: AppSQLContext, config: Config,
                                                actorSystem: ActorSystem, executionContext: ExecutionContext,
                                                @Named("fileDownloader") fileDownloader: ActorRef, galleryCache: GalleryCacheStore) extends Provider[GallerySaverContext] {

  def get(): GallerySaverContext = {
    val registry = LoaderRegistry()
    val gallerySaverDispatcher = actorSystem.actorOf(Props(classOf[GallerySaverDispatcher], Paths.get(config.getString("gallery-saver.destination")), galleryCache, fileDownloader, registry), "gallerySaverDispatcher")

    implicit val context = GallerySaverContext(config, sqlContext, executionContext, gallerySaverDispatcher, null, actorSystem, registry)

    context.registry
      .register(new PreviewLoader)
      .register(new ImageHostingLoader)

    context
  }
}
