package com.karasiq.gallerysaver.scripting.loaders

import akka.actor.ActorRef
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

import scala.concurrent.Future

/**
  * Basic gallery loader
  */
trait GalleryLoader {
  /**
    * Loader ID
    */
  def id: String

  /**
    * Is loader applicable to provided URL
    * @param url URL
    * @return Loader can load URL
    */
  def canLoadUrl(url: String): Boolean

  /**
    * Fetches resources from URL
    * @param url URL
    * @return Available resource
    */
  def load(url: String): Future[Iterator[LoadableResource]]

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  def load(resource: LoadableResource): Future[Iterator[LoadableResource]]

  /**
    * Custom file downloader
    */
  def fileDownloader: Option[ActorRef] = None
}
