import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.karasiq.gallerysaver.builtin.utils.PagedSiteImageExtractor
import com.karasiq.gallerysaver.scripting.loaders.HtmlUnitGalleryLoader
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

class AncensoredLoader extends HtmlUnitGalleryLoader with PagedSiteImageExtractor {
  /**
    * Loader ID
    */
  override def id: String = ???

  /**
    * Is loader applicable to provided URL
    *
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = ???

  /**
    * Fetches resources from URL
    *
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): GalleryResources = ???

  /**
    * Fetches sub resources from URL
    *
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): GalleryResources = ???

  override protected def nextPageOption(page: HtmlPage): Option[HtmlPage] = ???
}
