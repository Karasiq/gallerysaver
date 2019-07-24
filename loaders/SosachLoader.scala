import java.io.{OutputStream, PrintWriter}

import akka.NotUsed
import akka.actor.{ActorRef, Props}
import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder
import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import com.karasiq.common.{StringUtils, ThreadLocalFactory}
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.scripting.internal.{GallerySaverContext, LoaderUtils, Loaders}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.cloudflare.{CloudFlareCookieRetriever, CloudFlareUtils}
import com.karasiq.networkutils.downloader.{FileDownloaderActor, FileDownloaderTraits, HttpClientFileDownloader}
import com.karasiq.networkutils.url.URLParser

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

// Internal
object SosachParsers {
  val config: com.typesafe.config.Config = LoaderUtils.config.getConfig("gallery-saver.2ch")

  def fakeIp: Option[String] = {
    Try(config.getString("x-forwarded-for")).toOption
  }

  def session: Map[String, String] = {
    val userCodeAuth = Try(config.getString("session"))
    val cfClearance = Try(config.getString("cf-clearance"))

    Seq("usercode_auth" → userCodeAuth, "cf_clearance" → cfClearance)
      .collect {
        case (name, Success(value)) ⇒
          name → value
      }.toMap
  }

  val cloudFlareBypass = CloudFlareCookieRetriever()

  val cfWebClientFactory = ThreadLocalFactory.softRef[WebClient] {
    Try(config.getString("user-agent")) match {
      case Success(userAgent) ⇒
        val bv = new BrowserVersionBuilder(BrowserVersion.FIREFOX_52)
            .setUserAgent(userAgent)
            .build()

        HtmlUnitUtils.newWebClient(js = true, redirect = true, ignoreStatusCode = true, cache = new Cache,
          cookieManager = new CookieManager, browserVersion = bv)

      case Failure(_) ⇒
        HtmlUnitUtils.newWebClient(js = true, redirect = true, ignoreStatusCode = true, cache = new Cache,
          cookieManager = new CookieManager, browserVersion = BrowserVersion.FIREFOX_52)
    }
  }

  lazy val cfFileDownloader: ActorRef = {
    import com.karasiq.networkutils.HttpClientUtils._

    val webClient = cfWebClientFactory()

    val builder = defaultSettings.builder
      .setUserAgent(webClient.getBrowserVersion.getUserAgent)

    fakeIp.foreach { ip ⇒
      import org.apache.http.message.BasicHeader

      import scala.collection.JavaConversions._
      builder.setDefaultHeaders(Seq(new BasicHeader("X-Forwarded-For", ip)))
    }

    Option(webClient.getOptions.getProxyConfig).filter(_.getProxyHost ne null)
      .foreach(p ⇒ builder.setProxy(proxyConfigToProxy(p)))

    val history = LoaderUtils.fdHistory
    val converter = LoaderUtils.fdConverter
    val props = Props(new HttpClientFileDownloader(builder) with FileDownloaderActor with history.WithHistory with converter.WithImageConverter with FileDownloaderTraits.CheckSize with FileDownloaderTraits.CheckModified)
    LoaderUtils.actorSystem.actorOf(props, "sosachFileDownloader")
  }

  case class PostHeader(postId: Int, posterName: String, postTime: String, title: String, fileDescription: String) {
    def format = s"#$postId $posterName ($postTime) - $title [$fileDescription]"
  }

  case class Post(text: String, header: String, images: Seq[String] = Nil)

  case class Thread(url: String, posts: Seq[Post]) {
    def id: Option[Long] = {
      val regex = "/res/(\\d+).html".r
      regex.findFirstIn(url) match {
        case Some(regex(threadId)) ⇒
          Some(threadId.toLong)

        case _ ⇒
          None
      }
    }

    def opPost = posts.head

    def answers = posts.tail
  }

  trait ThreadParser {
    def parseThread(page: Page): Thread
  }

  object ThreadParser {
    def forPage(page: Page): ThreadParser = page match {
      case htmlPage: HtmlPage ⇒
        val footer = htmlPage.firstByXPath[HtmlFooter]("//p[@class='footer']|//footer[@class='footer']")
        footer match {
          case Some(ft) if ft.asText().contains("wakaba 3.0.8-mk2") ⇒
            new WakabaHtmlParser
          case _ ⇒
            new MakabaHtmlParser
        }

      case p ⇒
        throw new IllegalArgumentException("No parser found for page: " + p)
    }
  }

  object Thread {
    def apply(page: Page): Thread = ThreadParser.forPage(page).parseThread(page)

    def unapply(page: Page): Option[Thread] = {
      val thread = apply(page)
      if (thread.posts.isEmpty) None
      else Some(thread)
    }
  }

  /**
    * Old 2ch.hk engine HTML parser
    */
  private final class WakabaHtmlParser extends ThreadParser {
    def parseThread(page: Page) = page match {
      case htmlPage: HtmlPage ⇒ Thread(page.getUrl.toString, {
        htmlPage.byXPath[HtmlElement]("//div[@class='thread']/div[@class='oppost']|//div[@class='thread']/table[@class='post']/tbody/tr/td")
          .map(p ⇒ {
            val header = {
              val h = (p \\ classOf[HtmlSpan])
                .flatMap(span ⇒ span.classes.map(_ -> StringUtils.htmlTrim(span.getTextContent)))
                .toMap.withDefault(_ ⇒ "")
              val postId = "[\\d]+".r.findFirstIn(h("reflink")).fold(0)(_.toInt)
              PostHeader(postId, h("postername"), h("posttime"), h("filetitle"), h("filesize"))
            }
            val text = ((p \\ classOf[HtmlBlockQuote] *@\ "postMessage") \ classOf[HtmlParagraph])
              .fold("")(_.asText())
            val thumb = p \\ classOf[HtmlImage] *@\ "img"
            Post(text, header.format, thumb.map(_.getParentNode).collect {
              case a: HtmlAnchor ⇒ a.fullHref
            } orElse thumb.map(_.fullSrc) toVector)
          }).toIndexedSeq
      })
    }
  }

  /**
    * New 2ch.hk engine HTML parser
    */
  private final class MakabaHtmlParser extends ThreadParser {
    override def parseThread(page: Page): Thread = page match {
      case htmlPage: HtmlPage ⇒ Thread(page.getUrl.toString, {
        htmlPage.byXPath[HtmlElement]("//form[@id='posts-form']/div[1]/div/div").map(p ⇒ {
          val (details, text, images) = {
            val details: Map[String, String] = (p @\ "post-details" \\ classOf[HtmlSpan]).flatMap {
              e ⇒ e.classes.map(_ → StringUtils.htmlTrim(e.getTextContent))
            }.toMap.withDefaultValue("")

            val images = (p @\ "images" \\ classOf[HtmlImage])
              .filterNot(_.getSrcAttribute.contains("/makaba/templates/img/webm-logo.png"))
              .map { img ⇒ img.getParentNode match {
                case a: HtmlAnchor ⇒ a.fullHref
                case _ ⇒ img.fullSrc
              }
              }

            val text = (p \ classOf[HtmlBlockQuote]).fold("")(e ⇒ StringUtils.htmlTrim(e.asText()))

            (details, text, images.toIndexedSeq)
          }

          val header = {
            val (postTime, postId) = {
              val d = details("posttime-reflink").split('\n')
              (StringUtils.htmlTrim(d(0)), "[\\d]+".r.findFirstIn(d(2)).fold(0)(_.toInt))
            }
            PostHeader(postId, details("ananimas"), postTime, details("post-title"),
              images.map(img ⇒ URLParser(img).file.name).mkString(", "))
          }

          Post(text, header.format, images)
        }).toIndexedSeq
      })
    }
  }

  /**
    * Creates the text representation of thread and saves it to file
    */
  case class ThreadSaver(thread: Thread, hierarchy: Seq[String] = Seq("2ch", "unsorted"), referrer: Option[String] = Some("https://2ch.hk/"), cookies: Map[String, String] = Map.empty, loader: String = "sosach-thread") extends FileGenerator {
    private def separator = StringUtils.repeated("-", 100)

    private def formatPost(p: Post) = {
      s"${p.header}\n$separator\n${p.text}"
    }

    private def formatThread(writer: PrintWriter): Unit = {
      val heading = s"Thread: ${thread.url}"
      val formattedPosts = thread.posts.map(formatPost)
      writer.println(separator)
      writer.println(heading)
      writer.println(separator)
      formattedPosts.foreach { post ⇒
        writer.println()
        writer.println(separator)
        writer.println(post)
      }
    }

    override def write(os: OutputStream): Unit = {
      val writer = new PrintWriter(os)
      formatThread(writer)
      writer.flush()
    }

    /**
      * File name
      */
    override def fileName: Option[String] = thread.id.map(id ⇒ s"thread-$id.txt")

    /**
      * Resource URL
      */
    override def url: String = thread.url
  }

  def subDirectoryFor(thread: Thread, page: HtmlPage): Option[String] = {
    for {
      id <- thread.id
      title <- Some(StringUtils.htmlTrim(page.getTitleText)) if title.nonEmpty
    } yield PathUtils.validFileName(s"$title [$id]")
  }
}

object SosachResources {
  def thread(url: String, hierarchy: Seq[String] = Seq("2ch", "unsorted"), referrer: Option[String] = Some("https://2ch.hk/"), cookies: Map[String, String] = SosachParsers.session): GalleryResource = {
    GalleryResource("sosach-thread", url, referrer, cookies, hierarchy)
  }
}

class SosachLoader extends HtmlUnitGalleryLoader {
  import SosachParsers._
  private val log = LoaderUtils.log

  override def fileDownloader: Option[ActorRef] = Some(cfFileDownloader)
  override def webClient: WebClient = cfWebClientFactory()

  /**
    * Loader ID
    */
  override def id: String = "sosach-thread"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    """https?://m?(2ch|2-ch)\.\w{2}/\w+/res/\d+\.html?""".r.findFirstIn(url).nonEmpty
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    SosachResources.thread(url)
  }

  override protected def withResource[T <: LoadableResource](resource: LoadableResource, staticCookies: Map[String, String] = Map.empty)
                                                            (f: PartialFunction[Page, Source[T, NotUsed]])
                                                            (implicit ctx: GallerySaverContext): Source[T, NotUsed] = {
    val request = new WebRequest(new java.net.URL(resource.url), HttpMethod.GET)
    fakeIp.foreach { ip ⇒
      request.setAdditionalHeader("X-Forwarded-For", ip)
    }

    val wc = this.webClient
    wc.withCookies(createCookieManager(resource, staticCookies)) {
      val page: Page = concurrent.blocking(wc.getPage[Page](request))
      val result: Source[T, akka.NotUsed] = page match {
        case p: Page if CloudFlareUtils.isCloudFlareCaptchaPage(p) ⇒
          log.error("CloudFlare captcha required")
          log.info("Request: {}", p.getWebResponse.getWebRequest)
          Source.empty

        case p: Page if CloudFlareUtils.isCloudFlarePage(p) ⇒
          log.info("Bypassing CloudFlare page: {}", p)
          val wc = this.webClient
          wc.addCookies(cloudFlareBypass.retrieveCookies(p.getUrl))
          wc.withGetHtmlPage(resource.url)(f)

        case p: Page ⇒
          f(p)

        case _ ⇒
          Source.empty
      }
      page.cleanUp()
      result
    }
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page: HtmlPage ⇒
        val thread: Thread = Thread(page)
        val cookies = extractCookies(resource)
        val hierarchy = if (resource.hierarchy.lastOption.contains("unsorted")) {
          resource.hierarchy.dropRight(1) :+ subDirectoryFor(thread, page).getOrElse("unsorted")
        } else {
          resource.hierarchy
        }

        val textGenerator = if (thread.posts.nonEmpty) {
          Iterator.single(ThreadSaver(thread, hierarchy, Some(page.getUrl.toString), cookies))
        } else {
          Iterator.empty
        }

        Source.fromIterator(() ⇒ textGenerator ++ thread.posts.iterator
          .flatMap(_.images)
          .map(FileResource(this.id, _, Some(page.getUrl.toString), cookies, hierarchy)))
    }
  }
}

Loaders.register[SosachLoader]
