package com.karasiq.gallerysaver.dispatcher

import scala.collection.mutable.ListBuffer

import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader

trait LoaderRegistry {
  def register(loader: GalleryLoader): this.type
  def register[T <: GalleryLoader : Manifest]: this.type = {
    this.register(implicitly[Manifest[T]].runtimeClass.newInstance().asInstanceOf[T])
  }
  def forId(id: String): Option[GalleryLoader]
  def forUrl(url: String): Option[GalleryLoader]
  def idSet: Set[String]
}

object LoaderRegistry {
  def apply(): LoaderRegistry = {
    new LoaderRegistryImpl()
  }
}

private[dispatcher] final class LoaderRegistryImpl extends LoaderRegistry {
  private[this] val loaders = ListBuffer.empty[(String, GalleryLoader)]

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


