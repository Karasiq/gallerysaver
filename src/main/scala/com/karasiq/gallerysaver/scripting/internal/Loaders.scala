package com.karasiq.gallerysaver.scripting.internal

import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader

object Loaders {
  def register(loaders: GalleryLoader*)(implicit ctx: GallerySaverContext): this.type = {
    for (loader <- loaders) ctx.registry.register(loader)
    this
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
