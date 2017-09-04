import scala.language.{implicitConversions, postfixOps}
import scala.util.Try
import scala.util.matching.Regex

import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html._

import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._

trait TumblrLoader extends HtmlUnitGalleryLoader {
  override protected def compileCookies(resource: LoadableResource) = {
    val superCookies = super.compileCookies(resource)
    if (resource.url.contains("tumblr.com")) {
      superCookies ++ super.compileCookies(resource.url, TumblrParser.cookies().iterator)
    } else {
      superCookies
    }
  }
}

class TumblrPostLoader extends TumblrLoader {
  import TumblrParser.PostContent

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("tumblr.com/post/")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    TumblrResources.post(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page @ PostContent(urls @ _*) ⇒
        val files = urls.map(FileResource(this.id, _, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy))
        Source(files.toList)
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "tumblr-post"
}

class TumblrArchiveLoader extends TumblrLoader {
  import TumblrParser.Archive

  /**
    * Loader ID
    */
  override def id: String = "tumblr-archive"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("tumblr.com/archive")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = Source.single {
    TumblrResources.archive(url)
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case page @ Archive(name, posts) ⇒
        val cookies = extractCookies(resource)
        Source.fromIterator(() ⇒ posts.map(TumblrResources.post(_, resource.hierarchy :+ PathUtils.validFileName(name),
          Some(page.getUrl.toString), cookies)))
    }
  }
}

object TumblrResources {
  def post(url: String, hierarchy: Seq[String] = Seq("tumblr", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): CachedGalleryResource = {
    CachedGalleryResource("tumblr-post", url, referrer, cookies, hierarchy)
  }

  def archive(url: String, hierarchy: Seq[String] = Seq("tumblr"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): GalleryResource = {
    GalleryResource("tumblr-archive", url, referrer, cookies, hierarchy)
  }
}

object TumblrParser {
  def cookies(): Map[String, String] = {
    import scala.collection.JavaConverters._
    Try {
      val cookies = LoaderUtils.config.getConfig("gallery-saver.tumblr.cookies").entrySet().asScala.map { entry ⇒
        (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
      }
      cookies.toMap
    } getOrElse Map.empty
  }

  object Videos {
    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      Some(postVideos(htmlPage))
    }

    private[this] def postVideos(page: HtmlPage): Seq[String] = {
      def extractHDVideoUrl(video: HtmlVideo): Option[String] = {
        def fixJsonString(str: String): String = {
          str.replaceAllLiterally("\\/", "/")
            .replaceAllLiterally("\\\\", "\\")
        }

        val options = video.getAttribute("data-crt-options")
        val regex = "\"hdUrl\":\"([^\"]+)\"".r
        regex.findFirstMatchIn(options).map { m ⇒
          val url = m.group(1)
          val redirectUrl = LoaderUtils.fixUrl(fixJsonString(url))
          page.getWebClient.withGetPage(redirectUrl)((p: Page) ⇒ p.getUrl.toString)
        }
      }

      def extractPageVideos(page: HtmlPage): Iterator[String] = {
        page.descendantsBy { case video: HtmlVideo if video.hasAttribute("data-crt-options") ⇒
          extractHDVideoUrl(video)
        }.flatten
      }

      val post = "/html/body//*[starts-with(@class, 'post') or starts-with(@id, 'post')][1]"
      val iframeXPath = s"$post//div[contains(@id, 'post_')]//iframe"
      val videoXpath = s"$post//video[@data-crt-options]"
      val videos = page.byXPath[HtmlVideo](videoXpath).flatMap(extractHDVideoUrl)

      val iframeVideos = page.byXPath[HtmlInlineFrame](iframeXPath).map(_.getEnclosedPage).flatMap {
        case htmlPage: HtmlPage ⇒
          val videos = extractPageVideos(htmlPage).toList
          htmlPage.cleanUp()
          videos

        case _ ⇒
          Nil
      }
      videos.toList ++ iframeVideos
    }
  }

  object Images {
    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      Some(postImages(htmlPage))
    }

    private[this] def postImages(page: HtmlPage): Seq[String] = {
      val post = "/html/body//*[starts-with(@class, 'post') or starts-with(@id, 'post') or @id = 'Main'][1]"
      val imageXPath = s"$post//img[not(contains(@src, 'avatar'))]"
      val images: Iterator[HtmlImage] = page.byXPath[HtmlImage](imageXPath)
      val iframeXPath = s"$post//div[contains(@id, 'post_')]//iframe|$post//iframe[contains(@id, 'photoset_')]"
      val iframeImages = page.byXPath[HtmlInlineFrame](iframeXPath).map(_.getEnclosedPage).flatMap {
        case htmlPage: HtmlPage ⇒
          val images = htmlPage.images.toList
          htmlPage.cleanUp()
          images

        case _ ⇒
          Nil
      }

      (iframeImages ++ images).toList
        .collect(extractBestImage)
        .collect(downloadableUrl)
    }

    private[this] def extractBestImage: PartialFunction[AnyRef, AnyRef] = {
      case image: HtmlImage if image.getSrcAttribute.contains("media.tumblr.com") ⇒
        val anchor: Option[AnyRef] = image.getParentNode match {
          case a: HtmlAnchor if a.getHrefAttribute.contains("media.tumblr.com") ⇒
            Some(a)

          case a: HtmlAnchor if a.getHrefAttribute.contains("/image/") ⇒
            a.webClient.withGetHtmlPage(a.fullHref) { htmlPage ⇒
              htmlPage.firstByXPath[HtmlImage]("//img[@id='content-image']")
                .map(_.getAttribute("data-src"))
            }

          case _ ⇒
            None
        }
        anchor.getOrElse(image)
    }
  }

  object PostContent {
    def unapplySeq(page: HtmlPage): Option[Seq[String]] = {
      val content = Vector(Videos.unapplySeq(page), Images.unapplySeq(page)).flatten.flatten
      if (content.nonEmpty) Some(content) else None
    }
  }

  object Archive {
    def unapply(page: HtmlPage): Option[(String, Iterator[String])] = {
      for (blogName <- BlogName.unapply(page); iterator = posts(page))
        yield blogName → iterator
    }

    private def posts(page: HtmlPage): Iterator[String] = {
      // Pages
      val pages: Iterator[HtmlPage] = PaginationUtils.htmlPageIterator(page, _ match {
        case page @ NextPageURL(url) ⇒
          page.getWebClient.htmlPageOption(url)

        case _ ⇒
          None
      })

      // Posts
      val postsByPage = pages.map {
        case p @ Posts(posts @ _*) ⇒
          p.cleanUp()
          posts

        case p ⇒
          p.cleanUp()
          Nil
      }

      postsByPage.takeWhile(_.nonEmpty).flatten
    }

    object Posts {
      def unapplySeq(page: HtmlPage): Option[Seq[String]] = {
        for (anchors <- Some(postAnchors(page)) if anchors.nonEmpty)
          yield anchors.toList
      }

      private def postAnchors(page: HtmlPage): Iterator[String] = {
        page.descendantsBy {
          case post: HtmlAnchor if post.getHrefAttribute.contains("/post/") ⇒
            post.fullHref
        }
      }
    }

    private object BlogName {
      def unapply(page: HtmlPage): Option[String] = {
        tumblrUrlRegex.findFirstMatchIn(page.getUrl.toString)
          .map(_.group(1))
      }

      def tumblrUrlRegex: Regex = "(\\w+)\\.tumblr\\.com".r
    }

    private object NextPageURL {
      def unapply(page: HtmlPage): Option[String] = {
        page.firstByXPath[HtmlAnchor]("//div[@id='pagination']//a[@id='next_page_link']")
          .map(_.fullHref).filter(_ != page.getUrl.toString)
      }
    }
  }
}

Loaders
  .register[TumblrArchiveLoader]
  .register[TumblrPostLoader]