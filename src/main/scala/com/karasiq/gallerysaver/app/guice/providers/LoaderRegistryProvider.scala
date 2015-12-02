package com.karasiq.gallerysaver.app.guice.providers

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.builtin.{ImageHostingLoader, PreviewLoader}
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class LoaderRegistryProvider @Inject() (executionContext: ExecutionContext) extends Provider[LoaderRegistry] {
  private val registry = {
    new LoaderRegistryImpl()
      .register(new PreviewLoader(executionContext))
      .register(new ImageHostingLoader(executionContext))
  }

  override def get(): LoaderRegistry = {
    registry
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
}
