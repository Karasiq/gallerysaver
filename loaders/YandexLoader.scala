import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlLink, HtmlPage}
import com.karasiq.common.StringUtils
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.internal.Loaders
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.downloader.FileDownloader
import org.apache.commons.io.FilenameUtils

class YandexPhotoLoader extends HtmlUnitGalleryLoader {
  override def canLoadUrl(url: String): Boolean = {
    url.contains("fotki.yandex.ru") && url.contains("/view/")
  }

  override def load(url: String): GalleryResources = Source.single {
    YandexResources.photo(url)
  }

  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case YandexParser.Photo(url) ⇒
        Source.single(FileResource(this.id, url, Some(resource.url), extractCookies(resource), resource.hierarchy, Some(FilenameUtils.removeExtension(FileDownloader.fileNameFor(url, "")) + ".jpg")))
    }
  }

  override def id: String = "yandex-photo"
}

class YandexGalleryLoader extends HtmlUnitGalleryLoader {
  override def id: String = "yandex-gallery"

  override def canLoadUrl(url: String): Boolean = {
    url.contains("fotki.yandex.ru") && !url.contains("/view/")
  }

  override def load(url: String): GalleryResources = Source.single {
    YandexResources.gallery(url)
  }

  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case YandexParser.Gallery(title, images) ⇒
        Source.fromIterator(() ⇒ images.map(YandexResources.photo(_, resource.hierarchy :+ PathUtils.validFileName(title), Some(resource.url), extractCookies(resource))))
    }
  }
}

object YandexResources {
  def photo(url: String, hierarchy: Seq[String] = Seq("yandex", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map("fotki_adult" → "fotki_adult%3A0")): CachedGalleryResource = {
    CachedGalleryResource("yandex-photo", url, referrer, cookies, hierarchy)
  }

  def gallery(url: String, hierarchy: Seq[String] = Seq("yandex"), referrer: Option[String] = None, cookies: Map[String, String] = Map("fotki_adult" → "fotki_adult%3A0")): GalleryResource = {
    GalleryResource("yandex-gallery", url, referrer, cookies, hierarchy)
  }
}

object YandexParser {

  object Photo {
    def unapply(htmlPage: HtmlPage): Option[String] = {
      showOriginal(htmlPage)
        .orElse(imageSrc(htmlPage))
    }

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
  }

  object Gallery {
    def unapply(htmlPage: HtmlPage): Option[(String, Iterator[String])] = {
      for {
        title <- Some(Gallery.title(htmlPage))
        images <- Some(pages(htmlPage)) if images.nonEmpty
      } yield (title, images)
    }

    def title(htmlPage: HtmlPage): String = {
      StringUtils.htmlTrim(htmlPage.getTitleText)
    }

    def pages(htmlPage: HtmlPage): Iterator[String] = {
      PaginationUtils.htmlPageIterator(htmlPage, _.firstByXPath[HtmlAnchor]("//a[contains(@class, 'b-pager__next')]"))
        .map(images).takeWhile(_.nonEmpty).flatten
    }

    def images(htmlPage: HtmlPage): Iterator[String] = {
      htmlPage.byXPath[HtmlAnchor]("//div[contains(@class, 'preview-photos')]//a[contains(@class, 'photo')]")
        .map(_.fullHref)
    }
  }

}

Loaders.register(new YandexGalleryLoader, new YandexPhotoLoader)
