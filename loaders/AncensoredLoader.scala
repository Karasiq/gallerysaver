import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Cookie, CustomHeader, `User-Agent`}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlMeta, HtmlPage}
import com.karasiq.gallerysaver.builtin.utils.PagedSiteImageExtractor
import com.karasiq.gallerysaver.scripting.internal.Loaders
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._

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

class AncensoredClipLoader extends HtmlUnitGalleryLoader {
  val urlRegex = "https?://ancensored\\.com/clip/(.*)".r

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
      val hashRegex = "data: \\{hash: '([a-f0-9]+)'},".r

      val future = hashRegex.findFirstMatchIn(htmlPage.asXml()).map(_.group(1)).map { hash =>
        case class `X-CSRF-Token`(csrf: String) extends CustomHeader {
          override def name() = "X-CSRF-Token"
          override def value() = csrf
          override def lowercaseName() = "x-csrf-token"
          override def renderInRequests() = true
          override def renderInResponses() = false
        }

        case object `X-Requested-With` extends CustomHeader {
          override def name() = "X-Requested-With"
          override def value() = "XMLHttpRequest"
          override def lowercaseName() = "x-requested-with"
          override def renderInRequests() = true
          override def renderInResponses() = false
        }

        import scala.concurrent.duration._
        import com.karasiq.gallerysaver.scripting.internal.LoaderUtils._
        val http = Http()

        val csrf = htmlPage.elementsByTagName[HtmlMeta]("meta").find(_.getNameAttribute == "csrf-token").fold("")(_.getContentAttribute)

        val cookies = extractCookies("ancensored.com").toSeq
        val headers = Seq(
          `X-CSRF-Token`(csrf),
          `X-Requested-With`,
          `User-Agent`(htmlPage.getWebClient.getBrowserVersion.getUserAgent),
          Accept(MediaRange(MediaTypes.`application/json`))
        ) ++ (if (cookies.nonEmpty) Seq(Cookie(cookies: _*)) else Nil)

        http.singleRequest(HttpRequest(HttpMethods.POST, "http://ancensored.com/video/get-link", headers.toList, FormData("hash" -> hash).toEntity))
          .flatMap(resp => resp.entity.toStrict(10 seconds))
          .map(_.data.utf8String)
          .map(json => "\"src\":\"([^\"]+)\"".r.findFirstMatchIn(json).map(_.group(1)))
      }

      Source(future.toVector)
        .mapAsyncUnordered(1)(identity)
        .mapConcat(_.toVector)
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
    case a: HtmlAnchor if a.getHrefAttribute.startsWith("/clip/") => Source.single(a)
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

Loaders.register[AncensoredPicsLoader]
Loaders.register[AncensoredClipLoader]
Loaders.register[AncensoredVideosLoader]
Loaders.register[AncensoredCelebLoader]
