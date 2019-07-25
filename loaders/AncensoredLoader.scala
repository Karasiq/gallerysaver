import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Cookie, CustomHeader, `User-Agent`}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.BrowserVersion
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlMeta, HtmlPage}
import com.karasiq.gallerysaver.builtin.utils.PagedSiteImageExtractor
import com.karasiq.gallerysaver.scripting.internal.{AkkaHttpUtils, LoaderUtils, Loaders}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.concurrent.Future

object AncensoredResources {
  def pics(url: String, hierarchy: Seq[String] = Seq("ancensored", "pics"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): LoadableGallery = {
    GalleryResource("ancensored-pics", url, referrer, cookies, hierarchy)
  }

  def videos(url: String, hierarchy: Seq[String] = Seq("ancensored", "video"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): LoadableGallery = {
    GalleryResource("ancensored-videos", url, referrer, cookies, hierarchy)
  }

  def clip(url: String, hierarchy: Seq[String] = Seq("ancensored", "video", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): LoadableGallery = {
    CachedGalleryResource("ancensored-clip", url, referrer, cookies, hierarchy)
  }
}

class AncensoredPicsLoader extends HtmlUnitGalleryLoader with PagedSiteImageExtractor {
  val urlRegex = "https?://ancensored\\.com/\\w+/pics/(.*)".r

  /**
    * Loader ID
    */
  override def id: String = "ancensored-pics"

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.matches(urlRegex.regex)
  }

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources =
    Source.single(AncensoredResources.pics(url))

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = withResource(resource) {
    case htmlPage: HtmlPage =>
      val subDir = urlRegex.findFirstMatchIn(htmlPage.getUrl.toString).fold("unsorted")(_.group(1))
      getImagesSource(htmlPage).map { case (url, name) ⇒
        FileResource(this.id, url, Some(htmlPage.getUrl.toString), extractCookiesForUrl(url), resource.hierarchy :+ subDir, Some(name))
      }
  }

  override protected def nextPageOption(page: HtmlPage): Option[HtmlPage] =
    page.firstByXPath[HtmlAnchor]("//ul[@class='pagination']//li[@class='next']/a")
      .flatMap(_.tryClick)

  override protected def imageExpander = {
    case _ => Source.empty
  }
}

object AncensoredClipLoader extends LoaderUtils.ECImplicits {
  val urlRegex = "https?://ancensored\\.com/clip/(.*)".r

  def getCsrfToken(htmlPage: HtmlPage): String = {
    htmlPage.elementsByTagName[HtmlMeta]("meta")
      .find(_.getNameAttribute == "csrf-token")
      .fold("")(_.getContentAttribute)
  }

  def getVideoUrlJson(hash: String, csrf: String, cookies: Seq[(String, String)] = Nil, userAgent: String = BrowserVersion.BEST_SUPPORTED.getUserAgent) = {
    import com.karasiq.gallerysaver.scripting.internal.AkkaHttpUtils._
    val http = Http()

    val headers = Seq(
      `X-CSRF-Token`(csrf),
      `X-Requested-With`,
      `User-Agent`(userAgent),
      Accept(MediaRange(MediaTypes.`application/json`))
    ) ++ (if (cookies.nonEmpty) Seq(Cookie(cookies: _*)) else Nil)

    val request = HttpRequest(HttpMethods.POST, "http://ancensored.com/video/get-link", headers.toList, FormData("hash" -> hash).toEntity)
    execRequestToString(request)
  }

  def getVideoUrl(hash: String, csrf: String, cookies: Seq[(String, String)] = Nil, userAgent: String = BrowserVersion.BEST_SUPPORTED.getUserAgent) =
    getVideoUrlJson(hash, csrf, cookies, userAgent)
      .map(json => "\"src\":\"([^\"]+)\"".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(throw new IllegalArgumentException(json)))

  def getVideoUrlFromPage(htmlPage: HtmlPage, cookies: Seq[(String, String)] = Nil): Future[String] = {
    val userAgent = htmlPage.getWebClient.getBrowserVersion.getUserAgent
    val hashRegex = "data: \\{hash: '([a-f0-9]+)'},".r

    hashRegex.findFirstMatchIn(htmlPage.asXml()).map(_.group(1)) match {
      case Some(hash) =>
        val csrf = getCsrfToken(htmlPage)
        getVideoUrl(hash, csrf, cookies, userAgent)

      case None =>
        Future.failed(new IllegalArgumentException(s"Video hash not found: $htmlPage"))
    }
  }
}

class AncensoredClipLoader extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "ancensored-clip"

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.matches(AncensoredClipLoader.urlRegex.regex)
  }

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources =
    Source.single(AncensoredResources.videos(url))

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = withResource(resource) {
    case htmlPage: HtmlPage =>
      Source.fromFuture(AncensoredClipLoader.getVideoUrlFromPage(htmlPage, extractCookies("ancensored.com").toSeq))
        .map(url => FileResource(this.id, url, Some(htmlPage.getUrl.toString), extractCookiesForUrl(url), resource.hierarchy))
  }
}

class AncensoredVideosLoader extends HtmlUnitGalleryLoader with PagedSiteImageExtractor {
  val urlRegex = "https?://ancensored\\.com/\\w+/video/(.*)".r

  /**
    * Loader ID
    */
  override def id: String = "ancensored-videos"

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.matches(urlRegex.regex)
  }

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources =
    Source.single(AncensoredResources.videos(url))

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = withResource(resource) {
    case htmlPage: HtmlPage =>
      val subDir = urlRegex.findFirstMatchIn(htmlPage.getUrl.toString).fold("unsorted")(_.group(1))
      getImagesSource(htmlPage).map { case (url, _) ⇒
        AncensoredResources.clip(url, resource.hierarchy :+ subDir, Some(url), extractCookiesForUrl(url))
      }
  }

  override protected def nextPageOption(page: HtmlPage): Option[HtmlPage] =
    page.firstByXPath[HtmlAnchor]("//ul[@class='pagination']//li[@class='next']/a")
      .flatMap(_.tryClick)

  override protected def anchorExpander = {
    case a: HtmlAnchor if a.getHrefAttribute.nonEmpty && a.fullHref.matches(AncensoredClipLoader.urlRegex.regex) => Source.single(a)
  }

  override protected def imageExpander = {
    case _ => Source.empty
  }
}

class AncensoredCelebLoader extends HtmlUnitGalleryLoader {
  val urlRegex = "https?://ancensored\\.com/celebrities/([^/]+)".r

  /**
    * Loader ID
    */
  override def id = "ancensored-celeb"

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String) = url.matches(urlRegex.regex)

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String) = urlRegex.findFirstMatchIn(url) match {
    case Some(x) =>
      val name = x.group(1)
      val res = Seq(
        AncensoredResources.videos(s"http://ancensored.com/celebrities/video/$name"),
        AncensoredResources.pics(s"http://ancensored.com/celebrities/pics/$name")
      )
      Source(res.toVector)
    case None =>
      Source.empty
  }

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource) = load(resource.url)
}

Loaders.register(new AncensoredPicsLoader, new AncensoredClipLoader, new AncensoredVideosLoader, new AncensoredCelebLoader)
