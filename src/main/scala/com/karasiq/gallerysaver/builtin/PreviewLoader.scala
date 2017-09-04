package com.karasiq.gallerysaver.builtin

import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html.{HtmlImage, HtmlPage}

import com.karasiq.common.StringUtils
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.{ImageAnchor, ImagePreview}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{CacheableGallery, FileResource, LoadableFile, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

case class PreviewsResource(url: String, hierarchy: Seq[String] = Seq("previews", "unsorted"),
                            referrer: Option[String] = None, cookies: Map[String, String] = Map.empty,
                            loader: String = "previews") extends CacheableGallery

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
        val subFolder = getSubFolder(htmlPage)
        val links = htmlPage.descendantsBy {
          case ImagePreview(a) ⇒ a.fullHref
          case ImageAnchor(a) ⇒ a.fullHref
          case img: HtmlImage ⇒ img.fullSrc
        }
        Source(links.map(asResource(_, resource, subFolder)).toList)
    }
  }

  protected def asResource(url: String, resource: LoadableResource, subFolder: String): LoadableFile = {
    FileResource(this.id, url, Some(resource.url), extractCookies(resource),
      resource.hierarchy ++ Option(subFolder).filter(_.nonEmpty))
  }

  protected def getSubFolder(page: HtmlPage): String = {
    val urlHash = Integer.toHexString(page.getUrl.hashCode())
    val title = Option(page.getTitleText)
      .map(StringUtils.htmlTrim)
      .filter(_.nonEmpty)

    PathUtils.validFileName(title.fold(page.getUrl.toString)(title ⇒ s"$title [$urlHash]"), "_")
  }
}
