package com.karasiq.gallerysaver.builtin

import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html.{HtmlImage, HtmlPage}
import com.karasiq.gallerysaver.builtin.utils.{ImageAnchor, ImagePreview}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{CacheableGallery, FileResource, LoadableFile, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

case class PreviewsResource(url: String, hierarchy: Seq[String] = Seq("previews", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "previews") extends CacheableGallery

/**
  * Generic image loader
  */
class PreviewLoader(implicit ctx: GallerySaverContext) extends HtmlUnitGalleryLoader {
  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Always returns `true`
    */
  override def canLoadUrl(url: String): Boolean = true

  /**
    * Fetches resource from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    PreviewsResource(url, loader = this.id)
  }

  /**
    * Loader ID
    */
  override def id: String = "preview"

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case htmlPage: HtmlPage ⇒
        Source.fromIterator(() ⇒ htmlPage.descendantsBy {
          case ImagePreview(a) ⇒
            asResource(a.fullHref, resource)

          case ImageAnchor(a) ⇒
            asResource(a.fullHref, resource)

          case img: HtmlImage ⇒
            asResource(img.fullSrc, resource)
        })
    }
  }

  protected def asResource(url: String, resource: LoadableResource): LoadableFile = {
    FileResource(this.id, url, Some(resource.url), extractCookies(resource), resource.hierarchy)
  }
}
