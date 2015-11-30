package com.karasiq.gallerysaver.builtin

import com.karasiq.gallerysaver.scripting._

import scala.concurrent.{ExecutionContext, Future}

case class ImageHostingResource(url: String, hierarchy: Seq[String] = Seq("imagehosting", "unsorted"), loader: String = "image-hosting", referrer: Option[String] = None, cookies: Map[String, String] = Map.empty) extends CacheableGallery

/**
 * Image hosting expander
 */
class ImageHostingLoader(ec: ExecutionContext) extends GalleryLoader {
  /**
    * Loader ID
    */
  override def id: String = "image-hosting"

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  override def canLoadUrl(url: String): Boolean = {
    ImageHostingExtractor.partialFunction.isDefinedAt(url)
  }

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successful(Iterator(ImageHostingResource(url)))
  }

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = Future {
    resource.url match {
      case ImageHostingExtractor(data) ⇒
        data.map(FileResource(this.id, _, Some(resource.url), resource.cookies))

      case _ ⇒
        Iterator.empty
    }
  }(ec)
}
