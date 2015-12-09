import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage, HtmlMeta, HtmlPage}
import com.gargoylesoftware.htmlunit.{BrowserVersion, CookieManager, WebClient}
import com.karasiq.common.{StringUtils, ThreadLocalFactory}
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url.{URLParser, _}
import eu.timepit.refined.numeric.Positive
import shapeless.tag.@@

import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex
import scala.util.matching.Regex.Groups

object FlickrParser {
  def sessionCookie(): Option[(String, String)] = {
    if (Config.hasPath("gallery-saver.flickr.session")) {
      val value = Config.getString("gallery-saver.flickr.session")
      Some("cookie_session" → value)
    } else {
      None
    }
  }

  private val wcFactory = ThreadLocalFactory.softRef[WebClient](newWebClient(browserVersion = BrowserVersion.INTERNET_EXPLORER_11), _.close())

  /**
    * Compatible web client
    */
  def webClient(): WebClient = {
    wcFactory()
  }

  object Photos {
    /**
      * Extracts photo URLs from album page
      * @param htmlPage Web page of album
      * @return List of photo pages URL
      */
    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      val regex = """"ownerNsid":"([\w\d@]+)",.+?"id":"(\d+)"""".r
      val iterator = regex.findAllMatchIn(htmlPage.asXml()).collect {
        case Groups(path, id) ⇒
          s"https://www.flickr.com/photos/$path/$id/"
      }

      if (iterator.isEmpty) None else Some(iterator.toIndexedSeq)
    }
  }

  object Photo {
    private def cleanUrl(url: String) = {
      val regex = "(https?://(?:secure\\.|www\\.|)flickr\\.com/photos/[\\w-_@]+/[\\d]+)(?:/.*|)".r
      url match {
        case regex(photoUrl) ⇒
          photoUrl

        case _ ⇒
          throw new IllegalArgumentException(s"Invalid photo URL: $url")
      }
    }

    // Fast method
    private def originalSize(page: HtmlPage): Option[String] = {
      val regex = ",\"o\":\\{[^}]*\"url\":\"([^\"]*)\"".r
      regex.findFirstMatchIn(page.asXml()).collect {
        case Groups(originalUrl) ⇒
          s"http:${originalUrl.replaceAll("\\\\/", "/")}"
      }
    }

    // Another method (slow)
    private def getFromSizesPage(page: HtmlPage): Option[String] = {
      val webClient: WebClient = page.getWebClient
      def sizesPageUrl(size: String): String = s"${cleanUrl(page.getUrl.toString)}/sizes/$size"

      val bestSize = webClient.withGetPage(sizesPageUrl("t")) { htmlPage: HtmlPage ⇒
        htmlPage.byXPath[HtmlAnchor]("//ol[@class='sizes-list']/li/ol/li/a").toIterable.lastOption.fold(sizesPageUrl("o"))(_.fullHref)
      }

      webClient.withGetPage(bestSize) { imagePage: HtmlPage ⇒
        imagePage.firstByXPath[HtmlImage]("/html/body/div[*]/div[2]/img").map(_.fullSrc)
      }
    }

    /**
      * Selects maximum available size of photo
      * @param page Photo preview page
      * @return ID, title, and photo URL, or None if invalid URL provided
      */
    def unapply(page: HtmlPage): Option[(Long @@ Positive, String, String)] = {
      import eu.timepit.refined.refineT

      val id: Option[Long @@ Positive] = Try(URLParser(page.getUrl).file.name.toLong).toOption
        .flatMap(id ⇒ refineT[Positive](id).fold(_ ⇒ None, Some.apply))

      val title: String = page.firstByXPath[HtmlMeta]("//meta[@name='title']")
        .fold("Untitled")(meta ⇒ PathUtils.validFileName(meta.getContentAttribute.split('|')(0).take(50)))

      for {
        id <- id
        title <- Some(title)
        url <- originalSize(page).orElse(getFromSizesPage(page))
      } yield (id, title, url)
    }
  }

  object Search {
    def urlFor(query: String): String = {
      URLParser("https://www.flickr.com/search")
        .appendQuery("text" → query, "safe_search" → "3")
        .toURL.toString
    }

    def apply(query: String, pages: Int)(implicit wc: WebClient): Iterator[String] = {
      PaginationUtils.htmlPageIterator(urlFor(query), 1 to pages, "page").flatMap {
        case Photos(photos @ _*) ⇒
          photos

        case _ ⇒
          Nil
      }
    }
  }

  object PhotoStream {
    private def photos(page: HtmlPage): Iterator[String] = {
      val pages = PaginationUtils.htmlPageIterator(page, _.firstByXPath[HtmlAnchor]("//div[contains(@class, 'pagination-view')]/a[@rel='next']"))
      pages.flatMap {
        case Photos(photos @ _*) ⇒
          photos

        case _ ⇒
          Nil
      }
    }

    def unapply(page: HtmlPage): Option[(String, Iterator[String])] = {
      val title = page.getTitleText.split(Regex.quote("|"), 2).headOption.map(StringUtils.htmlTrim)
      for {
        title <- title
        images <- Some(photos(page)) if images.nonEmpty
      } yield (title, images)
    }
  }

  object FlickRiver {
    def apply(startUrl: String)(implicit wc: WebClient): Iterator[String] = {
      val pages = PaginationUtils.htmlPageIterator(startUrl + "?ajax&page=0", 0 to 1000).map { htmlPage ⇒
        htmlPage.byXPath[HtmlAnchor]("//div[@class='photo-img-container']/a[1]")
      }
      pages.takeWhile(_.nonEmpty).flatten.map(_.fullHref)
    }
  }
}

case class FlickrPhoto(url: String, hierarchy: Seq[String] = Seq("flickr", "unsorted"), referrer: Option[String] = None,
                       cookies: Map[String, String] = FlickrParser.sessionCookie().toMap, loader: String = "flickr-photo") extends CacheableGallery

case class FlickrGallery(url: String, hierarchy: Seq[String] = Seq("flickr"), referrer: Option[String] = None,
                       cookies: Map[String, String] = FlickrParser.sessionCookie().toMap, loader: String = "flickr-gallery") extends LoadableGallery

case class FlickrSearch(query: String, hierarchy: Seq[String] = Seq("flickr"), referrer: Option[String] = None,
                       cookies: Map[String, String] = FlickrParser.sessionCookie().toMap, loader: String = "flickr-search") extends InfiniteGallery {
  override def url: String = FlickrParser.Search.urlFor(this.query)
}

case class FlickRiverGallery(url: String, hierarchy: Seq[String] = Seq("flickr", "flickriver", "unsorted"), referrer: Option[String] = None,
                       cookies: Map[String, String] = FlickrParser.sessionCookie().toMap, loader: String = "flickriver") extends LoadableGallery

trait FlickrWebClient { self: HtmlUnitGalleryLoader ⇒
  override def webClient: WebClient = FlickrParser.webClient()

  override protected def compileCookies(resource: LoadableResource): Iterator[HtmlUnitCookie] = {
    val domains = Seq("flickr.com", "www.flickr.com", "secure.flickr.com")

    val cookies = for ((key, value) <- resource.cookies; domain <- domains) yield {
      new HtmlUnitCookie(domain, key, value, "/", 10000000, true)
    }

    cookies.toIterator
  }
}

class FlickrPhotoLoader extends HtmlUnitGalleryLoader with FlickrWebClient {
  import FlickrParser.Photo

  /**
    * Loader ID
    */
  override def id: String = "flickr-photo"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    val regex = "flickr.com/photos/[\\w@]+/\\d+/".r
    regex.findFirstIn(url).nonEmpty
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    FlickrPhoto(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case Photo(id, title, url) ⇒
        Iterator.single(FileResource(this.id, url, Some(resource.url), extractCookies(resource), resource.hierarchy, Some(s"$title [$id].jpg")))
    }
  }
}

class FlickrGalleryLoader extends HtmlUnitGalleryLoader with FlickrWebClient {
  import FlickrParser.PhotoStream

  /**
    * Loader ID
    */
  override def id: String = "flickr-gallery"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("flickr.com")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    FlickrGallery(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ PhotoStream(title, photos) ⇒
        photos.map(FlickrPhoto(_, resource.hierarchy :+ PathUtils.validFileName(title), Some(page.getUrl.toString)))
    }
  }
}

class FlickRiverLoader extends HtmlUnitGalleryLoader with FlickrWebClient {
  import FlickrParser.FlickRiver

  /**
    * Loader ID
    */
  override def id: String = "flickriver"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("flickriver.com/photos/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    FlickRiverGallery(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    val images = FlickRiver(resource.url)(webClient)
    images.map(FlickrPhoto(_, resource.hierarchy, cookies = extractCookies(resource)))
  }
}

class FlickrSearchLoader extends HtmlUnitGalleryLoader with FlickrWebClient {
  import FlickrParser.Photos

  /**
    * Loader ID
    */
  override def id: String = "flickr-search"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("flickr.com/search") && url.contains("text=")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    new GalleryResource(this.id, url, None, FlickrParser.sessionCookie().toMap, Seq("flickr")) with InfiniteGallery
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    val cookies = {
      val cm = new CookieManager
      this.compileCookies(resource).foreach(cm.addCookie)
      cm
    }

    val query = URLParser(resource.url).queryParser.toMap.getOrElse("text", "unknown")
    val subDir = PathUtils.validFileName(s"Search - $query")
    webClient.withCookies(cookies) {
      PaginationUtils.htmlPageIterator(resource.url, 1 to Int.MaxValue, "page")(webClient).map {
        case page @ Photos(photos @ _*) ⇒
          photos.map(FlickrPhoto(_, resource.hierarchy :+ subDir, Some(page.getUrl.toString), extractCookies(resource)))

        case _ ⇒
          Nil
      }.takeWhile(_.nonEmpty).flatten
    }
  }
}

Loaders
  .register[FlickrGalleryLoader]
  .register[FlickrSearchLoader]
  .register[FlickrPhotoLoader]
  .register[FlickRiverLoader]