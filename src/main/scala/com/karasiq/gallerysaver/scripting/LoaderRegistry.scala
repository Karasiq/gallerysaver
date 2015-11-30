package com.karasiq.gallerysaver.scripting

trait LoaderRegistry {
  def register(loader: GalleryLoader): this.type
  def register[T <: GalleryLoader : Manifest]: this.type = {
    this.register(implicitly[Manifest[T]].runtimeClass.newInstance().asInstanceOf[T])
  }
  def forId(id: String): Option[GalleryLoader]
  def forUrl(url: String): Option[GalleryLoader]
}


