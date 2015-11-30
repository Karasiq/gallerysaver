package com.karasiq.gallerysaver.app.guice

import com.karasiq.gallerysaver.scripting.{GalleryLoader, LoaderRegistry}

import scala.collection.concurrent.TrieMap

final class LoaderRegistryImpl extends LoaderRegistry {
  private val loaders = TrieMap.empty[String, GalleryLoader]

  def register(loader: GalleryLoader): this.type = {
    require(loader.id.ne(null) && loader.id.nonEmpty, "Invalid loader ID")
    loaders += loader.id → loader
    this
  }

  def forId(id: String): Option[GalleryLoader] = {
    loaders.get(id)
  }

  def forUrl(url: String): Option[GalleryLoader] = {
    loaders.valuesIterator.find(_.canLoadUrl(url))
  }
}
