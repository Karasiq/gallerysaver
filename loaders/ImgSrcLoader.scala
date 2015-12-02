import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage, HtmlPage, HtmlTableDataCell}
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{FileResource, LoadableGallery, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.concurrent.Future

case class ImgSrcGallery(url: String, hierarchy: Seq[String] = Seq("imgsrc"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "imgsrc-gallery") extends LoadableGallery

case class ImgSrcUser(url: String, hierarchy: Seq[String] = Seq("imgsrc"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "imgsrc-user") extends LoadableGallery

object ImgSrcParser {
  object UserGalleries {
    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      val galleryXpath = "/html/body/center/table/tbody/tr[3]/td/table/tbody/tr[position() > 2]/td[1]/a"
      for (galleries <- Some(htmlPage.byXPath[HtmlAnchor](galleryXpath)) if galleries.nonEmpty)
        yield galleries.map(_.fullHref).toStream
    }
  }

  object Gallery {
    private def skipForeword(page: HtmlPage): HtmlPage = {
      val forewordContinueXpath = "/html/body/center/table/tbody/tr[3]/td/center/table/tbody/tr/td[2]/a"
      page.firstByXPath[HtmlAnchor](forewordContinueXpath)
        .fold(page)(_.click[HtmlPage]())
    }

    private def galleryTapePage(page: HtmlPage): HtmlPage = {
      page.firstByXPath[HtmlAnchor]("/html/body/center/table/tbody/tr[3]/td/center/table[2]/tbody/tr/td/a[starts-with(@href, '/main/pic_tape.php')]")
        .fold(page)(_.click[HtmlPage]())
    }

    private def loadAllTape(page: HtmlPage): Iterator[String] = {
      val containerXpath = "/html/body/center/table/tbody/tr[3]/td/center"
      val imgXpath = s"$containerXpath//img[@class='big']"
      val nextPageXpath = s"$containerXpath//a[contains(., 'вперед')]"

      PaginationUtils.htmlPageIterator(page, _.firstByXPath[HtmlAnchor](nextPageXpath))
        .flatMap(_.byXPath[HtmlImage](imgXpath).map(_.fullSrc))
    }

    private def pageName(page: HtmlPage): Option[String] = {
      page.firstByXPath[HtmlTableDataCell]("/html/body/center/table/tbody/tr[1]/td[1]")
        .map(_.getTextContent.split("iMGSRC\\.RU").last.trim)
    }

    private def pageId(page: HtmlPage): Option[Int] = {
      "(\\d+)\\.html".r.findFirstMatchIn(page.getUrl.toString).map(_.group(1).toInt)
    }

    def unapply(htmlPage: HtmlPage): Option[(String, Int, Iterator[String])] = {
      val galleryPage = skipForeword(htmlPage)
      val tapePage = galleryTapePage(galleryPage)
      for {
        name <- pageName(tapePage)
        id <- pageId(galleryPage)
        images <- Some(loadAllTape(tapePage)) if images.nonEmpty
      } yield (name, id, images)
    }
  }
}

class ImgSrcUserLoader extends HtmlUnitGalleryLoader {
  import ImgSrcParser.UserGalleries

  /**
    * Loader ID
    */
  override def id: String = "imgsrc-user"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("imgsrc.ru/main/user.php")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(ImgSrcUser(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ UserGalleries(galleries @ _*) ⇒
        galleries.iterator.map(ImgSrcGallery(_, resource.hierarchy, Some(page.getUrl.toString)))
    }
  }
}

class ImgSrcGalleryLoader extends HtmlUnitGalleryLoader {
  import ImgSrcParser.Gallery

  /**
    * Loader ID
    */
  override def id: String = "imgsrc-gallery"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.matches("""https?://imgsrc\.ru/\w+/\d+\.html""") ||
      url.contains("imgsrc.ru/main/preword.php?")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(ImgSrcGallery(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ Gallery(title, id, images) ⇒
        val subDir = PathUtils.validFileName(s"$title [$id]")
        images.zipWithIndex.map { case (url, index) ⇒
          FileResource(this.id, url, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy :+ subDir, Some(index.toString))
        }
    }
  }
}

Loaders
  .register[ImgSrcGalleryLoader]
  .register[ImgSrcUserLoader]
