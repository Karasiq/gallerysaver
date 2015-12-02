package com.karasiq.gallerysaver.builtin

import com.gargoylesoftware.htmlunit.html.{HtmlImage, HtmlPage}
import com.karasiq.gallerysaver.builtin.utils.{ImageAnchor, ImagePreview}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{CacheableGallery, FileResource, LoadableFile, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.concurrent.{ExecutionContext, Future}

case class PreviewsResource(url: String, hierarchy: Seq[String] = Seq("previews", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "previews") extends CacheableGallery

/**
  * Generic image loader
  */
class PreviewLoader(ec: ExecutionContext) extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "preview"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Always returns `true`
    */
  override def canLoadUrl(url: String): Boolean = true

  protected def asResource(url: String, resource: LoadableResource): LoadableFile = {
    FileResource(this.id, url, Some(resource.url), extractCookies(resource), resource.hierarchy)
  }

  /**
    * Fetches resource from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(PreviewsResource(url, loader = this.id)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = Future {
    withResource(resource) {
      case htmlPage: HtmlPage ⇒
        htmlPage.descendantsBy {
          case ImagePreview(a) ⇒
            asResource(a.fullHref, resource)

          case ImageAnchor(a) ⇒
            asResource(a.fullHref, resource)

          case img: HtmlImage ⇒
            asResource(img.fullSrc, resource)
        }
    }
  }(ec)
}
