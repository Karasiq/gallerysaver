package com.karasiq.gallerysaver.scripting

trait LoaderRegistry {
  def register(loader: GalleryLoader): this.type
  def forId(id: String): Option[GalleryLoader]
  def forUrl(url: String): Option[GalleryLoader]
}


