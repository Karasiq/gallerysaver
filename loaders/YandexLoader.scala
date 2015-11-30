import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlLink, HtmlPage}
import com.gargoylesoftware.htmlunit.util.Cookie
import com.karasiq.common.StringUtils
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.PaginationUtils
import com.karasiq.gallerysaver.scripting._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.downloader.FileDownloader
import com.karasiq.networkutils.url._
import org.apache.commons.io.FilenameUtils

import scala.concurrent.Future

object YandexParser {
  object Photo {
    private def showOriginal(htmlPage: HtmlPage): Option[String] = {
      htmlPage
        .firstByXPath[HtmlAnchor]("//a[contains(@class, 'js-show-original')]")
        .map(_.fullHref)
    }

    private def imageSrc(htmlPage: HtmlPage): Option[String] = {
      htmlPage
        .firstByXPath[HtmlLink]("//link[@rel='image_src']")
        .map(_.fullUrl(_.getHrefAttribute))
    }

    def unapply(htmlPage: HtmlPage): Option[String] = {
      showOriginal(htmlPage)
        .orElse(imageSrc(htmlPage))
    }
  }

  object Gallery {
    def title(htmlPage: HtmlPage): String = {
      StringUtils.htmlTrim(htmlPage.getTitleText)
    }

    def images(htmlPage: HtmlPage): Iterator[String] = {
      htmlPage.byXPath[HtmlAnchor]("//div[contains(@class, 'preview-photos')]//a[contains(@class, 'photo')]")
        .map(_.fullHref)
    }

    def pages(htmlPage: HtmlPage): Iterator[String] = {
      PaginationUtils.htmlPageIterator(htmlPage, _.firstByXPath[HtmlAnchor]("//a[contains(@class, 'b-pager__next')]"))
        .map(images).takeWhile(_.nonEmpty).flatten
    }

    def unapply(htmlPage: HtmlPage): Option[(String, Iterator[String])] = {
      for {
        title <- Some(Gallery.title(htmlPage))
        images <- Some(pages(htmlPage)) if images.nonEmpty
      } yield (title, images)
    }
  }
}

class YandexPhotoLoader extends HtmlUnitGalleryLoader {

  webClient.addCookies(Set(new Cookie("fotki.yandex.ru", "fotki_adult", "fotki_adult%3A0", "/", 10000000, false)))

  /**
    * Loader ID
    */
  override def id: String = "yandex-photo"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("fotki.yandex.ru") && url.contains("/view/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(CachedGalleryResource(this.id, url, hierarchy = Seq("yandex", "unsorted"))))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    webClient.withGetHtmlPage(resource.url) {
      case YandexParser.Photo(url) ⇒
        Iterator(FileResource(this.id, url, Some(resource.url), extractCookies(resource), resource.hierarchy, Some(FilenameUtils.removeExtension(FileDownloader.fileNameFor(url, "")) + ".jpg")))

      case _ ⇒
        Iterator.empty
    }
  }
}

class YandexGalleryLoader extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "yandex-gallery"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("fotki.yandex.ru") && !url.contains("/view/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(GalleryResource(this.id, url, None, extractCookies("fotki.yandex.ru"), Seq("yandex"))))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    webClient.withGetHtmlPage(resource.url) {
      case YandexParser.Gallery(title, images) ⇒
        images.map { img ⇒
          CachedGalleryResource("yandex-photo", img, Some(resource.url), extractCookies(resource), resource.hierarchy :+ PathUtils.validFileName(title))
        }
    }
  }
}

Loaders
  .register[YandexPhotoLoader]
  .register[YandexGalleryLoader]
