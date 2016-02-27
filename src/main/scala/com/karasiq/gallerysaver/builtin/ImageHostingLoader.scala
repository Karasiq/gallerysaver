package com.karasiq.gallerysaver.builtin

import akka.stream.scaladsl.Source
import com.karasiq.gallerysaver.builtin.utils.ImageHostingExtractor
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{CacheableGallery, FileResource, LoadableResource}

case class ImageHostingResource(url: String, hierarchy: Seq[String] = Seq("imagehosting", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "image-hosting") extends CacheableGallery

/**
 * Image hosting expander
 */
class ImageHostingLoader(implicit ctx: GallerySaverContext) extends GalleryLoader {
  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    ImageHostingExtractor.predefinedExtractors.isDefinedAt(url)
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    ImageHostingResource(url, loader = this.id)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    resource.url match {
      case ImageHostingExtractor(data) ⇒
        Source.fromIterator(() ⇒ data.map(FileResource(this.id, _, Some(resource.url), resource.cookies)))

      case _ ⇒
        Source.empty
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "image-hosting"
}
