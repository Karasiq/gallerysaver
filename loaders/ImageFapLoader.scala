import java.net.URLEncoder

import scala.language.{implicitConversions, postfixOps}

import akka.stream.scaladsl._
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html._

import com.karasiq.gallerysaver.builtin.utils._
import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

// TODO: Catalog, search
object ImageFapResources {
  def gallery(url: String, hierarchy: Seq[String] = Seq("imagefap", "unsorted"), referrer: Option[String] = None,
              cookies: Map[String, String] = Map.empty): CachedGalleryResource = {
    CachedGalleryResource("imagefap-gallery", url, referrer, cookies, hierarchy)
  }

  def folder(url: String, hierarchy: Seq[String] = Seq("imagefap", "unsorted"), referrer: Option[String] = None,
             cookies: Map[String, String] = Map.empty): GalleryResource = {
    GalleryResource("imagefap-folder", url, referrer, cookies, hierarchy)
  }

  def user(url: String, hierarchy: Seq[String] = Seq("imagefap", "unsorted"), referrer: Option[String] = None,
           cookies: Map[String, String] = Map.empty): GalleryResource = {
    GalleryResource("imagefap-user", url, referrer, cookies, hierarchy)
  }
}

object ImageFapParser {
  private[this] implicit def iteratorToSeqOption[T](iterator: Iterator[T]): Option[Seq[T]] = {
    if (iterator.isEmpty) None else Some(iterator.toVector)
  }

  object Folders {
    private def isFolderAnchor(a: HtmlAnchor): Boolean = {
      val url = a.getHrefAttribute
      a.getAttribute("class") == "blk_galleries" && url.contains("folderid=") && !url.contains("folderid=0")
    }

    def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
      val anchors = htmlPage.descendantsBy {
        case a: HtmlAnchor if isFolderAnchor(a) ⇒
          a.fullHref
      }
      iteratorToSeqOption(anchors).map(_.distinct)
    }
  }

  object Folder {
    sealed trait ContentType
    case object Gallery extends ContentType
    case object Image extends ContentType

    object Images {
      def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
        val images = htmlPage.descendantsBy {
          case img: HtmlImage if img.getSrcAttribute.contains("/images/thumb/") ⇒
            img.fullSrc.replaceAll("/thumb/", "/full/")
        }
        images
      }
    }

    object Galleries {
      def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
        val xpath = "//table[contains(@class, 'blk_galleries') or contains(@class, 'blk_favorites')]/tbody/tr/td[2]//a[contains(@href, '/gallery/') or (contains(@href, '/gallery.php?') and contains(@href, 'gid='))]"

        htmlPage.byXPath[HtmlAnchor](xpath).map(_.fullHref)
      }
    }

    def apply(page: HtmlPage): Iterator[(ContentType, Seq[String])] = {
      PaginationUtils.htmlPageIterator(page, p ⇒ htmlElementOptionClick(p.elementOption(_.getAnchorByText(":: next ::")))).flatMap { htmlPage ⇒
        val galleries = htmlPage match {
          case Galleries(g @ _*) ⇒
            Gallery → g

          case _ ⇒
            Gallery → Nil
        }

        val images = htmlPage match {
          case Images(img @ _*) ⇒
            Image → img

          case _ ⇒
            Image → Nil
        }

        val content = Seq(galleries, images).filter(_._2.nonEmpty)
        if (content.nonEmpty) content else Some(Image → Nil)
      } takeWhile(_._2.nonEmpty)
    }

    def unapply(htmlPage: HtmlPage): Option[(Int, String)] = {
      id(htmlPage) → name(htmlPage) match {
        case (Some(id), Some(name)) ⇒
          Some(id → name)

        case _ ⇒
          None
      }
    }

    def id(htmlPage: HtmlPage): Option[Int] = {
      "(?:/organizer/|\\bfolderid=)([\\d]+)\\b".r.findFirstMatchIn(htmlPage.getUrl.toString).map(_.group(1).toInt)
    }

    def name(htmlPage: HtmlPage): Option[String] = {
      htmlPage.firstByXPath[HtmlAnchor]("/html/body/center/table[2]/tbody/tr/td/table/tbody/tr/td/div/center/center/table/tbody/tr/td/table/tbody/tr/td[2]/table/tbody/tr/td/a")
        .map(_.getTextContent.replaceAll("\\W+", "_"))
    }
  }

  object Gallery {
    private def galleryTitleXpath: String = "//div[@id='menubar']/table/tbody/tr/td[2]/table/tbody/tr/td/b[1]/font"

    object Images {
      def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
        htmlPage.byXPath[HtmlAnchor]("//div[@id='gallery']//a[contains(@href, '/photo/')]")
          .map(_.fullHref)
      }
    }

    def apply(htmlPage: HtmlPage): Iterator[String] = {
      PaginationUtils.htmlPageIterator(htmlPage, _.firstByXPath[HtmlAnchor]("//a[text()=':: next ::']")).flatMap {
        case Images(images @ _*) ⇒
          images

        case _ ⇒
          Nil
      }
    }

    def unapply(htmlPage: HtmlPage): Option[(Int, String)] = {
      id(htmlPage) → name(htmlPage) match {
        case (Some(id), Some(name)) ⇒
          Some(id → name)

        case _ ⇒
          None
      }
    }

    def id(htmlPage: HtmlPage): Option[Int] = {
      "(?:/(?:gallery|pictures)/|\\bgid=)([\\d]+)\\b".r.findFirstMatchIn(htmlPage.getUrl.toString).map(_.group(1).toInt)
    }

    def name(htmlPage: HtmlPage): Option[String] = {
      htmlPage.firstByXPath[HtmlFont](galleryTitleXpath)
        .map(_.getTextContent)
    }
  }

  object Image {
    def unapply(htmlPage: HtmlPage): Option[HtmlImage] = {
      htmlPage.firstByXPath[HtmlImage]("//img[@id='mainPhoto']")
    }
  }

  object Catalog {
    object Galleries {
      def unapplySeq(htmlPage: HtmlPage): Option[Seq[String]] = {
        val xpath = "/html/body/center/table[2]/tbody/tr/td[1]/table/tbody/tr/td/div/center/table/tbody/tr/td[2]/form//a[contains(@href, '/gallery/') or (contains(@href, '/gallery.php?') and contains(@href, 'gid='))]"
        htmlPage.byXPath[HtmlAnchor](xpath).map(_.fullHref)
      }
    }

    def apply(url: String, maxPages: Int)(implicit wc: WebClient): Iterator[String] = {
      PaginationUtils.htmlPageIterator(url, 0 until maxPages).flatMap {
        case ImageFapParser.Catalog.Galleries(galleries @ _*) ⇒
          galleries

        case _ ⇒
          Nil
      }
    }
  }

  object Search {
    def url(query: String, parameters: Map[String, _] = Map.empty): String = {
      val parametersString = (parameters + ("search" → query))
        .map { case (name, value) ⇒ s"$name=${URLEncoder.encode(value.toString, "UTF-8")}" } mkString "&"

      s"http://www.imagefap.com/gallery.php?$parametersString"
    }
  }
}

class ImageFapGalleryLoader extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "imagefap-gallery"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("imagefap.com/pictures/") || url.contains("imagefap.com/gallery/") || url.contains("imagefap.com/gallery.php?")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = {
    Source.single(ImageFapResources.gallery(url))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case htmlPage @ ImageFapParser.Gallery(id, name) ⇒
        val subDir: String = s"${name.take(100).replaceAll("\\W+", "_")} [$id]"
        val hierarchy = if (resource.hierarchy.lastOption.contains("unsorted"))
          resource.hierarchy.dropRight(1) :+ LoaderUtils.tagFor(name)
        else
          resource.hierarchy
        Source.fromIterator(() ⇒ ImageFapParser.Gallery(htmlPage).zipWithIndex)
          .flatMapConcat {
            case (url, index) ⇒
              Source
                .fromFuture(LoaderUtils.future(webClient.htmlPageOption(url)))
                .flatMapConcat {
                  case Some(ImageFapParser.Image(img)) ⇒
                    Source.single(FileResource(this.id, LoaderUtils.fixUrl(img.fullSrc), Some(url), resource.cookies, hierarchy :+ subDir, Some(index.toString)))

                  case _ ⇒
                    Source.empty
                }
          }
    }
  }
}

class ImageFapFolderLoader extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "imagefap-folder"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("imagefap.com") && (url.contains("/organizer/") || url.contains("folderid="))
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = {
    Source.single(ImageFapResources.folder(url))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case htmlPage @ ImageFapParser.Folder(id, name) ⇒
        val subDir: String = s"Folder - $name [$id]"
        val hierarchy = if (resource.hierarchy.lastOption.contains("unsorted"))
          resource.hierarchy.dropRight(1) :+ LoaderUtils.tagFor(name)
        else
          resource.hierarchy

        Source.fromIterator(() ⇒ ImageFapParser.Folder(htmlPage))
          .flatMapConcat {
            case (ImageFapParser.Folder.Gallery, galleries) ⇒
              Source(galleries.toVector).map(url ⇒ ImageFapResources.gallery(url, hierarchy :+ subDir, Some(htmlPage.getUrl.toString), resource.cookies))

            case (ImageFapParser.Folder.Image, images) ⇒
              Source(images.toVector).map(url ⇒ FileResource(this.id, url, Some(htmlPage.getUrl.toString), resource.cookies, hierarchy :+ subDir))
          }
    }
  }
}

class ImageFapUserLoader extends HtmlUnitGalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "imagefap-user"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    url.contains("imagefap.com/profile/") || url.contains("imagefap.com/showfavorites.php?") || url.contains("imagefap.com/usergallery.php?")
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = {
    Source.single(ImageFapResources.user(url))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = {
    withResource(resource) {
      case htmlPage @ ImageFapParser.Folders(folders @ _*) ⇒
        Source(folders.toVector).map(ImageFapResources.folder(_, resource.hierarchy, Some(htmlPage.getUrl.toString), resource.cookies))
    }
  }
}

Loaders
  .register[ImageFapGalleryLoader]
  .register[ImageFapFolderLoader]
  .register[ImageFapUserLoader]