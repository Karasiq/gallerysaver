import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.html._
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.internal.{LoaderUtils, Loaders}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.collection.GenTraversableOnce
import scala.concurrent.Future
import scala.language.{implicitConversions, postfixOps}

class TumblrPostLoader extends HtmlUnitGalleryLoader {

  import TumblrParser.Images

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
      case page@Images(images) ⇒
        images.map(FileResource(this.id, _, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy))
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "tumblr-post"
}

class TumblrArchiveLoader extends HtmlUnitGalleryLoader {

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
      case page@Archive(name, posts) ⇒
        Source.fromIterator(() ⇒ posts.map(TumblrResources.post(_, resource.hierarchy :+ PathUtils.validFileName(name), Some(page.getUrl.toString), extractCookies(resource))))
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

  object Images {
    def unapply(htmlPage: HtmlPage): Option[Source[String, akka.NotUsed]] = {
      Some(postImages(htmlPage))
    }

    private def postImages(page: HtmlPage): Source[String, akka.NotUsed] = {
      val photo: Iterator[HtmlImage] = page.byXPath[HtmlImage]("/html/body//img[not(contains(@src, 'avatar'))]")
      val iframesPhoto: Iterator[HtmlImage] = page.byXPath[HtmlInlineFrame]("//div[contains(@id, 'post_')]//iframe").map(_.getEnclosedPage).flatMap {
        case htmlPage: HtmlPage ⇒
          htmlPage.images

        case _ ⇒
          Nil
      }

      (iframesPhoto ++ photo).expandFilter(postImageExpander).collect(downloadableUrl)
    }

    private def postImageExpander: PartialFunction[AnyRef, Source[AnyRef, akka.NotUsed]] = {
      case image: HtmlImage if image.getSrcAttribute.contains("media.tumblr.com") ⇒
        val anchor: Future[GenTraversableOnce[AnyRef]] = LoaderUtils.future {
          image.getParentNode match {
            case a: HtmlAnchor if a.getHrefAttribute.contains("media.tumblr.com") ⇒
              Some(a)

            case a: HtmlAnchor if a.getHrefAttribute.contains("/image/") ⇒
              a.webClient.withGetHtmlPage(a.fullHref) {
                case htmlPage: HtmlPage ⇒
                  htmlPage.firstByXPath[HtmlImage]("//img[@id='content-image']").map(_.getAttribute("data-src"))
              }

            case _ ⇒
              Nil
          }
        }

        Source.fromFuture(anchor)
          .flatMapConcat { anchor ⇒
            if (anchor.isEmpty) {
              Source.single(image)
            } else {
              Source.fromIterator(() ⇒ anchor.toIterator)
            }
          }
    }
  }

  object Archive {

    def unapply(page: HtmlPage): Option[(String, Iterator[String])] = {
      for (blogName <- BlogName.unapply(page); iterator <- Some(posts(page)))
        yield blogName → iterator
    }

    private def posts(page: HtmlPage): Iterator[String] = {
      // Pages
      val pages: Iterator[HtmlPage] = PaginationUtils.htmlPageIterator(page, _ match {
        case page@NextPageURL(url) ⇒
          page.getWebClient.htmlPageOption(url)

        case _ ⇒
          None
      })

      // Posts
      val postsByPage = pages.map {
        case Posts(posts@_*) ⇒
          posts

        case _ ⇒
          Nil
      }

      postsByPage.takeWhile(_.nonEmpty).flatten
    }

    private object BlogName {
      def unapply(page: HtmlPage): Option[String] = {
        tumblrUrlRegex.findFirstMatchIn(page.getUrl.toString)
          .map(_.group(1))
      }

      def tumblrUrlRegex = "(\\w+)\\.tumblr\\.com".r
    }

    private object NextPageURL {
      def unapply(page: HtmlPage): Option[String] = {
        page.firstByXPath[HtmlAnchor]("//div[@id='pagination']//a[@id='next_page_link']")
          .map(_.fullHref).filter(_ != page.getUrl.toString)
      }
    }

    object Posts {
      def unapplySeq(page: HtmlPage): Option[Seq[String]] = {
        for (anchors <- Some(postAnchors(page)) if anchors.nonEmpty)
          yield anchors.toStream
      }

      private def postAnchors(page: HtmlPage): Iterator[String] = {
        page.descendantsBy {
          case post: HtmlAnchor if post.getHrefAttribute.contains("/post/") ⇒
            post.fullHref
        }
      }
    }

  }

}

Loaders
  .register[TumblrArchiveLoader]
  .register[TumblrPostLoader]