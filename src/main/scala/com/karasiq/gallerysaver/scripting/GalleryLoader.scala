package com.karasiq.gallerysaver.scripting

import akka.actor.ActorRef

import scala.concurrent.Future

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
    * Fetches resource from URL
    * @param url URL
    * @return Available resource
    */
  def load(url: String): Future[Option[LoadableResource]]

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  def load(resource: LoadableResource): Future[Iterator[LoadableResource]]

  /**
    * Custom file downloader
    */
  def fileDownloader: Option[ActorRef]
}
