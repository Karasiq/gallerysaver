package com.karasiq.gallerysaver.builtin

import akka.stream.scaladsl.Source

import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.{ImageExpander, ImageHostingExtractor}
import com.karasiq.gallerysaver.builtin.utils.ImageHostingExtractor.CapturedPage
import com.karasiq.gallerysaver.scripting.internal.{GallerySaverContext, LoaderUtils}
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
    val future = LoaderUtils.future {
      resource.url match {
        case ImageHostingExtractor(CapturedPage(url, title, cookies, files)) ⇒
          Source.fromIterator(() ⇒ {
            val linksIterator = files.iterator.map(ImageExpander.downloadableUrl)

            val hierarchy = if (resource.hierarchy.lastOption.contains("unsorted"))
              resource.hierarchy.dropRight(1) :+ LoaderUtils.tagFor(title)
            else
              resource.hierarchy

            val folderName = PathUtils.validFileName(s"$title [${url.hashCode.toHexString}]")
            linksIterator.map(FileResource(this.id, _, Some(url), resource.cookies ++ cookies, hierarchy :+ folderName))
          })

        case _ ⇒
          Source.empty
      }
    }
    Source.fromFuture(future).flatMapConcat(identity)
  }

  /**
    * Loader ID
    */
  override def id: String = "image-hosting"
}
