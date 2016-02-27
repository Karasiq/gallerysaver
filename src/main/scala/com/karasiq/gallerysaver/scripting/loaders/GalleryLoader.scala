package com.karasiq.gallerysaver.scripting.loaders

import akka.actor.ActorRef
import akka.stream.scaladsl.Source
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

/**
  * Basic gallery loader
  */
trait GalleryLoader {
  final type GalleryResources = Source[LoadableResource, akka.NotUsed]

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
  def load(url: String): GalleryResources

  /**
    * Fetches sub resources from URL
    * @param resource Parent resource
    * @return Available resources
    */
  def load(resource: LoadableResource): GalleryResources

  /**
    * Custom file downloader
    */
  def fileDownloader: Option[ActorRef] = None
}
