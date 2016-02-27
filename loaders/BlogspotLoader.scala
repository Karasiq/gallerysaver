import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlPage}
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.gallerysaver.builtin.utils.PagedSiteImageExtractor
import com.karasiq.gallerysaver.scripting.internal.{LoaderUtils, Loaders}
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.{FileResource, GalleryResource, LoadableGallery, LoadableResource}
import com.karasiq.networkutils.HtmlUnitUtils._

import scala.concurrent.Future

object BlogspotBlog {
  def apply(url: String, hierarchy: Seq[String] = Seq("blogspot"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty): LoadableGallery = {
    GalleryResource("blogspot-blog", url, referrer, cookies, hierarchy)
  }
}

class BlogspotLoader extends HtmlUnitGalleryLoader with PagedSiteImageExtractor {
  override protected def imageExpander: ExpanderFunction[AnyRef] = {
    sequencedFilter(previews, ImageHostingFile().orElse(extensionFilter().asExpanderFunction))
  }

  override protected def nextPageOption(page: HtmlPage): Option[HtmlPage] = {
    page.firstByXPath[HtmlAnchor]("//div[@id='blog-pager']//a[@class='blog-pager-older-link']").flatMap { a ⇒
      page.getWebClient.htmlPageOption(a.fullHref)
    }
  }

  private val pageRegex = """https?://([\w-]+)\.blogspot\.\w{2,4}/""".r

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    pageRegex.findFirstIn(url).nonEmpty
  }

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    BlogspotBlog(url)
  }

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    val subDir = pageRegex.findFirstMatchIn(resource.url).fold("unsorted")(_.group(1))
    withResource(resource) {
      case htmlPage: HtmlPage ⇒
        val page = htmlPage
          .firstByXPath[HtmlAnchor]("//a[contains(@class, 'maia-button maia-button-primary')]")
          .fold(htmlPage)(_.click[HtmlPage]()) // Skip disclaimer

        getImagesIterator(page).map { case (url, name) ⇒
          FileResource(this.id, url, Some(htmlPage.getUrl.toString), extractCookiesForUrl(url), resource.hierarchy :+ subDir, Some(name))
        }
    }
  }

  /**
    * Loader ID
    */
  override def id: String = "blogspot-blog"
}

Loaders.register[BlogspotLoader]