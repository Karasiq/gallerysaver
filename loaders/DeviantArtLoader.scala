import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage, HtmlPage}
import com.karasiq.common.StringUtils
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting._
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.concurrent.Future

case class DeviantArtPhoto(url: String, hierarchy: Seq[String] = Seq("deviantart", "unsorted"), referrer: Option[String] = None,
                       cookies: Map[String, String] = Map("agegate_state" → "1"), loader: String = "deviantart-photo") extends CacheableGallery

case class DeviantArtGallery(url: String, hierarchy: Seq[String] = Seq("deviantart"), referrer: Option[String] = None,
                         cookies: Map[String, String] = Map("agegate_state" → "1"), loader: String = "deviantart-gallery") extends LoadableGallery

object DeviantArtParser {
  object Photo {
    private def downloadButton(htmlPage: HtmlPage): Option[String] = {
      htmlPage.firstByXPath[HtmlAnchor]("//a[contains(@class, 'dev-page-download')]")
        .filterNot(_.getHrefAttribute.startsWith("javascript://"))
        .map { a ⇒
          // Traverse redirects
          val page = a.click[Page]()
          val url = page.getUrl.toString
          page.cleanUp()
          url
        }
    }

    private def imageFull(htmlPage: HtmlPage): Option[String] = {
      htmlPage.firstByXPath[HtmlImage]("//img[contains(@class, 'dev-content-full')]")
        .map(_.fullSrc)
    }

    private def imageNormal(htmlPage: HtmlPage): Option[String] = {
      htmlPage.firstByXPath[HtmlImage]("//a[contains(@class, 'dev-content-normal')]")
        .map(_.fullSrc)
    }

    def unapply(htmlPage: HtmlPage): Option[String] = {
      downloadButton(htmlPage)
        .orElse(imageFull(htmlPage))
        .orElse(imageNormal(htmlPage))
    }
  }

  object Gallery {
    def title(htmlPage: HtmlPage): String = {
      StringUtils.htmlTrim(htmlPage.getTitleText)
    }

    def pages(htmlPage: HtmlPage): Iterator[HtmlPage] = {
      PaginationUtils.htmlPageIterator(htmlPage, _.firstByXPath[HtmlAnchor]("//div[@id='gallery_pager']//li[contains(@class, 'next')]//a[@id='gmi-GPageButton']"))
    }

    def images(htmlPage: HtmlPage): Seq[String] = {
      htmlPage.byXPath[HtmlAnchor]("//a[contains(@class, 'thumb')]")
        .filterNot(_.getHrefAttribute.startsWith("javascript:"))
        .map(_.fullHref)
        .toIndexedSeq
    }

    def unapply(htmlPage: HtmlPage): Option[(String, Iterator[String])] = {
      for {
        title <- Some(Gallery.title(htmlPage)).filter(_.nonEmpty)
        images <- Some(pages(htmlPage).flatMap(Gallery.images)).filter(_.nonEmpty)
      } yield (title, images)
    }
  }
}

class DeviantArtPhotoLoader extends HtmlUnitGalleryLoader {
  import DeviantArtParser.Photo

  /**
    * Loader ID
    */
  override def id: String = "deviantart-photo"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("deviantart.com/art/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(DeviantArtPhoto(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ Photo(image) ⇒
        Iterator(FileResource(this.id, image, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy))
    }
  }
}

class DeviantArtGalleryLoader extends HtmlUnitGalleryLoader {
  import DeviantArtParser.Gallery

  /**
    * Loader ID
    */
  override def id: String = "deviantart-gallery"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("deviantart.com/gallery/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(DeviantArtGallery(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ Gallery(title, images) ⇒
        images.map(DeviantArtPhoto(_, resource.hierarchy :+ PathUtils.validFileName(title), Some(page.getUrl.toString), extractCookies(resource)))
    }
  }
}

Loaders
  .register[DeviantArtGalleryLoader]
  .register[DeviantArtPhotoLoader]