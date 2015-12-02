import com.gargoylesoftware.htmlunit.html._
import com.karasiq.fileutils.PathUtils
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.gallerysaver.builtin.utils.PaginationUtils
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{CacheableGallery, FileResource, LoadableGallery, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.collection.GenTraversableOnce
import scala.concurrent.Future
import scala.language.{implicitConversions, postfixOps}

case class TumblrPost(url: String, hierarchy: Seq[String] = Seq("tumblr", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "tumblr-post") extends CacheableGallery

case class TumblrArchive(url: String, hierarchy: Seq[String] = Seq("tumblr"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "tumblr-archive") extends LoadableGallery

object TumblrParser {
  object Images {
    private def postImageExpander: PartialFunction[AnyRef, GenTraversableOnce[AnyRef]] = {
      case image: HtmlImage if image.getSrcAttribute.contains("media.tumblr.com") ⇒
        val anchor: GenTraversableOnce[AnyRef] = image.getParentNode match {
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

        if (anchor.nonEmpty) anchor else Some(image)
    }

    private def postImages(page: HtmlPage): Iterator[String] = {
      val photo: Iterator[HtmlImage] = page.byXPath[HtmlImage]("/html/body//img[not(contains(@src, 'avatar'))]")
      val iframesPhoto: Iterator[HtmlImage] = page.byXPath[HtmlInlineFrame]("//div[contains(@id, 'post_')]//iframe").map(_.getEnclosedPage).flatMap {
        case htmlPage: HtmlPage ⇒
          htmlPage.images

        case _ ⇒
          Nil
      }

      (iframesPhoto ++ photo).expandFilter(postImageExpander).toIterator.collect(downloadableUrl)
    }

    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      for(images <- Some(postImages(htmlPage)) if images.nonEmpty)
        yield images.toStream
    }
  }

  object Archive {
    private object BlogName {
      def tumblrUrlRegex = "(\\w+)\\.tumblr\\.com".r

      def unapply(page: HtmlPage): Option[String] = {
        tumblrUrlRegex.findFirstMatchIn(page.getUrl.toString)
          .map(_.group(1))
      }
    }

    private object NextPageURL {
      def unapply(page: HtmlPage): Option[String] = {
        page.firstByXPath[HtmlAnchor]("//div[@id='pagination']//a[@id='next_page_link']")
          .map(_.fullHref).filter(_ != page.getUrl.toString)
      }
    }

    object Posts {
      private def postAnchors(page: HtmlPage): Iterator[String] = {
        page.descendantsBy {
          case post: HtmlAnchor if post.getHrefAttribute.contains("/post/") ⇒
            post.fullHref
        }
      }

      def unapplySeq(page: HtmlPage): Option[Seq[String]] = {
        for (anchors <- Some(postAnchors(page)) if anchors.nonEmpty)
          yield anchors.toStream
      }
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
        case Posts(posts @ _*) ⇒
          posts

        case _ ⇒
          Nil
      }

      postsByPage.takeWhile(_.nonEmpty).flatten
    }

    def unapply(page: HtmlPage): Option[(String, Iterator[String])] = {
      for (blogName <- BlogName.unapply(page); iterator <- Some(posts(page)))
        yield blogName → iterator
    }
  }
}

class TumblrPostLoader extends HtmlUnitGalleryLoader {
  import TumblrParser.Images

  /**
    * Loader ID
    */
  override def id: String = "tumblr-post"

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
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(TumblrPost(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ Images(images @ _*) ⇒
        images.iterator.map(FileResource(this.id, _, Some(page.getUrl.toString), extractCookies(resource), resource.hierarchy))
    }
  }
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
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(TumblrArchive(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    withResource(resource) {
      case page @ Archive(name, posts) ⇒
        posts.map(TumblrPost(_, resource.hierarchy :+ PathUtils.validFileName(name), Some(page.getUrl.toString), extractCookies(resource)))
    }
  }
}

Loaders
  .register[TumblrArchiveLoader]
  .register[TumblrPostLoader]