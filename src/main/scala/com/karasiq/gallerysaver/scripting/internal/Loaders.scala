package com.karasiq.gallerysaver.scripting.internal

import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader

object Loaders {
  def register(loader: GalleryLoader)(implicit ctx: GallerySaverContext): this.type = {
    ctx.registry.register(loader)
    this
  }

  def register[T <: GalleryLoader : Manifest](implicit ctx: GallerySaverContext): this.type = {
    this.register(implicitly[Manifest[T]].runtimeClass.newInstance().asInstanceOf[T])
  }

  def idSet(implicit ctx: GallerySaverContext): Set[String] = {
    ctx.registry.idSet
  }

  def forUrl(url: String)(implicit ctx: GallerySaverContext): Option[GalleryLoader] = {
    ctx.registry.forUrl(url)
  }

  def forId(id: String)(implicit ctx: GallerySaverContext): Option[GalleryLoader] = {
    ctx.registry.forId(id)
  }
}
