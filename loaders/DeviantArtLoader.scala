import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage, HtmlPage}
import com.karasiq.common.StringUtils
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.internal.Loaders
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._


class DeviantArtPhotoLoader extends HtmlUnitGalleryLoader {

  import DeviantArtParser.Photo

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
  override def load(url: String): GalleryResources = {
    Source.single(DeviantArtResources.photo(url))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page@Photo(image) ⇒
        Source.single(FileResource(this.id, image, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy))
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "deviantart-photo"
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
  override def load(url: String): GalleryResources = {
    Source.single(DeviantArtResources.gallery(url))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page@Gallery(title, images) ⇒
        Source.fromIterator(() ⇒ images.map(DeviantArtResources.photo(_, resource.hierarchy :+ PathUtils.validFileName(title), Some(page.getUrl.toString), extractCookies(resource))))
    }
  }
}

object DeviantArtResources {
  def photo(url: String, hierarchy: Seq[String] = Seq("deviantart", "unsorted"), referrer: Option[String] = None,
            cookies: Map[String, String] = Map("agegate_state" → "1"), loader: String = "deviantart-photo"): CachedGalleryResource = {
    CachedGalleryResource(loader, url, referrer, cookies, hierarchy)
  }

  def gallery(url: String, hierarchy: Seq[String] = Seq("deviantart"), referrer: Option[String] = None,
              cookies: Map[String, String] = Map("agegate_state" → "1"), loader: String = "deviantart-gallery"): GalleryResource = {
    GalleryResource(loader, url, referrer, cookies, hierarchy)
  }
}

object DeviantArtParser {

  object Photo {
    def unapply(htmlPage: HtmlPage): Option[String] = {
      downloadButton(htmlPage)
        .orElse(imageFull(htmlPage))
        .orElse(imageNormal(htmlPage))
    }

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
  }

  object Gallery {
    def unapply(htmlPage: HtmlPage): Option[(String, Iterator[String])] = {
      for {
        title <- Some(Gallery.title(htmlPage)).filter(_.nonEmpty)
        images <- Some(pages(htmlPage).flatMap(Gallery.images)).filter(_.nonEmpty)
      } yield (title, images)
    }

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
  }

}

Loaders
  .register[DeviantArtGalleryLoader]
  .register[DeviantArtPhotoLoader]