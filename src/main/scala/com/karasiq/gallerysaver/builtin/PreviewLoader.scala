package com.karasiq.gallerysaver.builtin

import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage}
import com.karasiq.gallerysaver.scripting._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.concurrent.{ExecutionContext, Future}

case class PreviewsResource(url: String, hierarchy: Seq[String] = Seq("previews", "unsorted"), loader: String = "previews", referrer: Option[String] = None, cookies: Map[String, String] = Map.empty) extends CacheableGallery

private object ImagePreview {
  def unapply(img: HtmlImage): Option[HtmlAnchor] = {
    img.getParentNode match {
      case a: HtmlAnchor ⇒
        Some(a)

      case _ ⇒
        None
    }
  }
}

private object ImageAnchor {
  private val defaultImageExtensions = Set("jpg", "jpeg", "bmp", "png", "gif")

  def unapply(anchor: HtmlAnchor): Option[HtmlAnchor] = anchor match {
    case a: HtmlAnchor if defaultImageExtensions.contains(URLParser(a.fullHref).file.extension.toLowerCase) ⇒
      Some(a)

    case _ ⇒
      None
  }
}

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
    * @return Always returns `false` to not conflict with other loaders
    */
  override def canLoadUrl(url: String): Boolean = false

  protected def asResource(url: String, resource: LoadableResource): LoadableFile = {
    FileResource(this.id, url, Some(resource.url), resource.cookies, resource.hierarchy)
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
    webClient.withGetHtmlPage(resource.url)(_.descendantsBy {
      case ImagePreview(a) ⇒
        asResource(a.fullHref, resource)

      case ImageAnchor(a) ⇒
        asResource(a.fullHref, resource)

      case img: HtmlImage ⇒
        asResource(img.fullSrc, resource)
    })
  }(ec)
}
