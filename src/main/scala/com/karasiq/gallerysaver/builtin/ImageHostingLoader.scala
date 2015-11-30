package com.karasiq.gallerysaver.builtin

import com.gargoylesoftware.htmlunit.html._
import com.karasiq.gallerysaver.scripting.{CacheableGallery, FileResource, HtmlUnitGalleryLoader, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.collection.GenTraversableOnce
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

case class ImageHostingResource(url: String, hierarchy: Seq[String] = Seq("imagehosting", "unsorted"), loader: String = "image-hosting", referrer: Option[String] = None, cookies: Map[String, String] = Map.empty) extends CacheableGallery

/**
 * Image hosting expander
 */
class ImageHostingLoader(ec: ExecutionContext) extends HtmlUnitGalleryLoader {
  @inline
  private def extractImage(url: String, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): Iterator[AnyRef] = {
    webClient.withGetHtmlPage(url)(getImage).toIterator
  }

  private def downloadableUrl: PartialFunction[AnyRef, String] = {
    case a: HtmlAnchor ⇒
      a.fullHref

    case img: HtmlImage ⇒
      img.fullSrc

    case s: String ⇒
      s
  }

  /**
   * Expand image hosting by custom predicate
   * @param predicate Predicate function
   * @param getImage Image extractor function
   * @return Hosting expander function
   */
  def expandImageHostingC(predicate: String ⇒ Boolean, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): PartialFunction[AnyRef, Iterator[AnyRef]] = {
    case a: HtmlAnchor if predicate(a.fullHref) ⇒
      extractImage(a.fullHref, getImage)

    case url: String if predicate(url) ⇒
      extractImage(url, getImage)
  }

  /**
   * Expand image hosting by regular expression
   * @param regex URL regular expression
   * @param getImage Image extractor function
   * @return Hosting expander function
   */
  def expandImageHostingR(regex: String, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): PartialFunction[AnyRef, Iterator[AnyRef]] = {
    val urlRegex = "^https?://(www\\.)?" + regex + ".*$"
    expandImageHostingC(_.matches(urlRegex), getImage)
  }

  /**
   * Expand image hosting by substring
   * @param urlStart URL literal substring
   * @param getImage Image extractor function
   * @return Hosting expander function
   */
  def expandImageHosting(urlStart: String, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): PartialFunction[AnyRef, Iterator[AnyRef]] = {
    expandImageHostingR(Regex.quote(urlStart), getImage)
  }

  private val imageHostingExpandFuncs: PartialFunction[AnyRef, Iterator[AnyRef]] = {
    Vector(
      expandImageHosting("postimg.org/image/",
        _.elementOption(_.getFirstByXPath[HtmlImage]("/html/body/center/img"))),

      expandImageHosting("fotki.yandex.ru/",
        _.firstByXPath[HtmlLink]("/html/head/link[@rel='image_src']").map(_.getHrefAttribute)),

      expandImageHostingR("(img\\d+\\.)?imagevenue\\.com/img\\.php\\?",
        _.elementOption(_.getHtmlElementById[HtmlImage]("thepic"))),

      expandImageHosting("hostingpics.net/viewer.php?",
        _.elementOption(_.getHtmlElementById[HtmlImage]("img_viewer"))),

      expandImageHostingC(url ⇒ (url.contains("imageshack.us") || url.contains("imageshack.com")) && !url.contains("/download/"),
        _.firstByXPath[HtmlAnchor]("//a[contains(@href, '/download/')]")),

      expandImageHosting("vfl.ru/fotos/",
        _.elementOption(_.getHtmlElementById[HtmlImage]("img_foto"))),

      expandImageHosting("vfl.ru/i/",
        _.firstByXPath[HtmlImage]("/html/body/div/img")),

      expandImageHosting("imageban.ru/show/",
        _.elementOption(_.getHtmlElementById[HtmlImage]("img_obj"))),

      expandImageHosting("ifotki.info/",
        _.firstByXPath[HtmlInput]("/html/body/center[1]/table/tbody/tr/td/input[2]").map {
          case input: HtmlInput ⇒
            // Extracting image URL
            input.getValueAttribute
              .split(Regex.quote("[IMG]")).last
              .split(Regex.quote("[/IMG]")).head
        }),

      expandImageHosting("imagebam.com/image/",
        _.firstByXPath[HtmlAnchor]("/html/body//a[contains(@title, 'Save')]")),

      expandImageHosting("hostingfailov.com/photo/",
        _.elementOption(_.getHtmlElementById[HtmlImage]("thepic"))),

      expandImageHosting("piccy.info/view/",
        _.elementOption(_.getHtmlElementById[HtmlImage]("mainim"))),

      expandImageHosting("fastpic.ru/view/",
        _.elementOption(_.getHtmlElementById[HtmlImage]("image"))),

      expandImageHostingR("""jpegshare\.net/(?!images/)""",
        _.firstByXPath[HtmlImage]("/html/body/table/tbody/tr[1]/td/img")),

      expandImageHosting("saveimg.ru/show-image.php?",
        _.firstByXPath[HtmlImage]("//table[@id='content']/tbody/tr[2]/td//img")),

      expandImageHosting("ii4.ru/image-",
        _.firstByXPath[HtmlImage]("/html/body/div[2]/div[2]/img")),

      expandImageHostingR("savepic\\.ru/\\d+m?\\.htm",
        _.firstByXPath[HtmlImage]("/html/body/div/div[2]/div[1]/p[1]/a/img")),

      expandImageHostingR("(radikal\\.ru|f-picture\\.net)/(F|fp)/",
        _.firstByXPath[HtmlImage]("//img[@itemprop='contentUrl']"))
    ).reduce(_ orElse _)
}

  /**
    * Loader ID
    */
  override def id: String = "image-hosting"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    imageHostingExpandFuncs.isDefinedAt(url)
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(PreviewsResource(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = Future {
    imageHostingExpandFuncs(resource.url).collect(downloadableUrl).map { fileUrl ⇒
      FileResource(this.id, fileUrl, Some(resource.url))
    }
  }(ec)
}
