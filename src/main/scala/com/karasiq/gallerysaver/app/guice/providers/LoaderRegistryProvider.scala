package com.karasiq.gallerysaver.app.guice.providers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.builtin.{ImageHostingLoader, PreviewLoader}
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class LoaderRegistryProvider @Inject()(mapDbFile: MapDbFile, config: Config, actorSystem: ActorSystem, executionContext: ExecutionContext) extends Provider[LoaderRegistry] {
  final implicit val context = GallerySaverContext(config, mapDbFile, executionContext, ActorRef.noSender, null, actorSystem, new LoaderRegistryImpl())

  context.registry
    .register(new PreviewLoader)
    .register(new ImageHostingLoader)

  override def get(): LoaderRegistry = {
    context.registry
  }
}

final class LoaderRegistryImpl extends LoaderRegistry {
  private val loaders = ListBuffer.empty[(String, GalleryLoader)]

  def register(loader: GalleryLoader): this.type = {
    require(loader.id.ne(null) && loader.id.nonEmpty, "Invalid loader ID")
    loaders += loader.id → loader
    this
  }

  def forId(id: String): Option[GalleryLoader] = {
    loaders.reverseIterator.find(_._1 == id).map(_._2)
  }

  def forUrl(url: String): Option[GalleryLoader] = {
    loaders.reverseIterator.find(_._2.canLoadUrl(url)).map(_._2)
  }

  override def idSet: Set[String] = {
    loaders.map(_._1).toSet
  }
}
