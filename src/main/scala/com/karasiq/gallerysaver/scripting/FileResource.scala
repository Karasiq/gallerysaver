package com.karasiq.gallerysaver.scripting

case class FileResource(loader: String, url: String, referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, hierarchy: Seq[String] = Seq.empty, fileName: Option[String] = None) extends LoadableFile

case class GalleryResource(loader: String, url: String, referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, hierarchy: Seq[String] = Seq.empty) extends LoadableGallery

case class CachedGalleryResource(loader: String, url: String, referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, hierarchy: Seq[String] = Seq.empty) extends CacheableGallery