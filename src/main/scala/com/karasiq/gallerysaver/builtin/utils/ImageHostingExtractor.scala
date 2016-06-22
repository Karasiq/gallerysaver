package com.karasiq.gallerysaver.builtin.utils

import com.gargoylesoftware.htmlunit.CookieManager
import com.gargoylesoftware.htmlunit.html._
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.collection.GenTraversableOnce
import scala.util.matching.Regex

object ImageHostingExtractor {
  private val webClient = newWebClient(js = false, redirect = true, cookieManager = createCookieManager())

  def createCookieManager(): CookieManager = {
    val cm = new CookieManager
    cm.addCookie(new HtmlUnitCookie("vfl.ru", "vfl_ero", "1", "/", 1000000, false))
    cm.addCookie(new HtmlUnitCookie("vfl.ru", "vfl_antid", "1", "/", 1000000, false))
    cm
  }

  def unifyToUrl: PartialFunction[AnyRef, String] = {
    case ImagePreview(a) ⇒
      a.fullHref

    case a: HtmlAnchor ⇒
      a.fullHref

    case url: String ⇒
      url
  }

  /**
    * Expand image hosting by custom predicate
    * @param predicate Predicate function
    * @param getImage Image extractor function
    * @return Hosting expander function
    */
  def expandImageHostingC(predicate: String ⇒ Boolean, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): PartialFunction[AnyRef, Iterator[AnyRef]] = {
    case v if unifyToUrl.isDefinedAt(v) && predicate(unifyToUrl(v)) ⇒
      extractImage(unifyToUrl(v), getImage)
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

  def predefinedExtractors: PartialFunction[AnyRef, Iterator[AnyRef]] = {
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
        _.firstByXPath[HtmlImage]("//img[@data-original]").flatMap { image ⇒
          def attr(name: String) = Option(image.getAttribute(name)).filter(_.nonEmpty).map(url ⇒ image.fullUrl(_ ⇒ url))
          attr("data-original")
            .orElse(attr("src"))
        }),

      expandImageHosting("ifotki.info/",
        _.firstByXPath[HtmlInput]("/html/body/center[1]/table/tbody/tr/td/input[2]").map { input ⇒
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

  def unapply(a: AnyRef): Option[Iterator[String]] = {
    predefinedExtractors.andThen(_.collect(ImageExpander.downloadableUrl)).lift(a)
  }

  @inline
  private def extractImage(url: String, getImage: HtmlPage ⇒ GenTraversableOnce[AnyRef]): Iterator[AnyRef] = {
    Iterator.fill(1) {
      webClient.withCookies(createCookieManager()) {
        webClient.withGetHtmlPage(url)(getImage)
      }
    }.flatMap(_.toIterator)
  }
}
