import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage, HtmlPage, HtmlTableDataCell}
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.internal.Loaders
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{FileResource, GalleryResource, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

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
  override def load(url: String): GalleryResources = Source.single {
    ImgSrcResources.user(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page @ UserGalleries(galleries @ _*) ⇒
        Source(galleries.toVector.map(ImgSrcResources.gallery(_, resource.hierarchy, Some(page.getUrl.toString))))
    }
  }
}

class ImgSrcGalleryLoader extends HtmlUnitGalleryLoader {
  import ImgSrcParser.Gallery

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.matches("""https?://imgsrc\.ru/\w+/a?\d+\.html""") ||
      url.contains("imgsrc.ru/main/preword.php?")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    ImgSrcResources.gallery(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page@Gallery(title, id, images) ⇒
        val subDir = PathUtils.validFileName(s"$title [$id]")
        Source.fromIterator(() ⇒ images.zipWithIndex.map { case (url, index) ⇒
          FileResource(this.id, url, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy :+ subDir, Some(index.toString))
        })
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "imgsrc-gallery"
}

object ImgSrcResources {
  def gallery(url: String, hierarchy: Seq[String] = Seq("imgsrc"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): GalleryResource = {
    GalleryResource("imgsrc-gallery", url, referrer, cookies, hierarchy)
  }

  def user(url: String, hierarchy: Seq[String] = Seq("imgsrc"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): GalleryResource = {
    GalleryResource("imgsrc-user", url, referrer, cookies, hierarchy)
  }
}

object ImgSrcParser {
  object UserGalleries {
    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      val galleryXpath = "/html/body/table/tbody/tr[3]/td/table/tbody/tr[position() > 2]/td[1]/a"
      for (galleries <- Some(htmlPage.byXPath[HtmlAnchor](galleryXpath)) if galleries.nonEmpty)
        yield galleries.map(_.fullHref).toVector
    }
  }

  object Gallery {
    def unapply(htmlPage: HtmlPage): Option[(String, Int, Iterator[String])] = {
      val galleryPage = skipForeword(htmlPage)
      val tapePage = galleryTapePage(galleryPage)
      for {
        name <- pageName(tapePage)
        id <- pageId(galleryPage)
        images <- Some(loadAllTape(tapePage)) if images.nonEmpty
      } yield (name, id, images)
    }

    private def skipForeword(page: HtmlPage): HtmlPage = {
      val forewordContinueXpath = "/html/body/center/table/tbody/tr[3]/td/center/table/tbody/tr/td[2]/a"
      page.firstByXPath[HtmlAnchor](forewordContinueXpath)
        .fold(page)(_.click[HtmlPage]())
    }

    private def galleryTapePage(page: HtmlPage): HtmlPage = {
      page.firstByXPath[HtmlAnchor]("/html/body/table/tbody/tr[3]/td/center/a[starts-with(@href, '/main/tape.php')]")
        .fold(page)(_.click[HtmlPage]())
    }

    private def loadAllTape(page: HtmlPage): Iterator[String] = {
      val containerXpath = "/html/body/table/tbody/tr[3]/td"
      val imgXpath = s"$containerXpath//img[contains(@class, 'big')]"
      val nextPageXpath = s"$containerXpath/table/tbody/tr/td/a[contains(., '►')]"

      PaginationUtils.htmlPageIterator(page, _.firstByXPath[HtmlAnchor](nextPageXpath).flatMap(a ⇒ page.getWebClient.htmlPageOption(a.fullHref)))
        .flatMap(_.byXPath[HtmlImage](imgXpath).flatMap { img =>
          def getAttr(attr: String) = Option(img.getAttribute(attr)).filter(_.nonEmpty)
          getAttr("data-src").orElse(getAttr("src")).map(url ⇒ img.fullUrl(_ ⇒ url))
        })
    }

    private def pageName(page: HtmlPage): Option[String] = {
      page.firstByXPath[HtmlTableDataCell]("/html/body/table/tbody/tr[1]/td[1]")
        .map(_.getTextContent.split("iMGSRC\\.RU").last.trim)
    }

    private def pageId(page: HtmlPage): Option[Int] = {
      "(\\d+)\\.html".r.findFirstMatchIn(page.getUrl.toString).map(_.group(1).toInt)
    }
  }

}

Loaders.register(new ImgSrcGalleryLoader, new ImgSrcUserLoader)
