package com.karasiq.gallerysaver.dispatcher

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.karasiq.gallerysaver.scripting.{LoadableFile, LoadableGallery, LoaderRegistry}

import scala.util.{Failure, Success}

class GallerySaverDispatcher(fileDownloader: ActorRef, loaders: LoaderRegistry) extends Actor with ActorLogging {
  import context.dispatcher

  override def receive: Receive = {
    case url: String ⇒
      loaders.forUrl(url) match {
        case Some(loader) ⇒
          val sender = this.sender()
          log.info("Fetching URL: {}", url)
          loader.load(url).onComplete {
            case Success(resources) ⇒
              sender ! LoadedResources(resources.toStream)

            case Failure(exc) ⇒
              log.error(exc, "Error loading URL: {}", url)
              sender ! LoadedResources.empty
          }

        case None ⇒
          log.warning("Loader not found for URL: {}", url)
          sender() ! None
      }

    case f: LoadableFile ⇒
      val loader = loaders.forId(f.loader).flatMap(_.fileDownloader).getOrElse(fileDownloader)
      log.info("Loading file: {}", f)
      //loader ! f.asFileToDownload
      sender() ! LoadedResources.empty

    //case cg: CacheableGallery ⇒
      // TODO: Cacheable gallery

    case g: LoadableGallery ⇒
      loaders.forId(g.loader).orElse(loaders.forUrl(g.url)) match {
        case Some(loader) ⇒
          val sender = this.sender()
          log.info("Loading resource: {}", g)
          loader.load(g).onComplete {
            case Success(resources) ⇒
              sender ! LoadedResources(resources.toStream)

            case Failure(exc) ⇒
              log.error(exc, "Error loading resource: {}", g)
              sender ! LoadedResources.empty
          }

        case None ⇒
          log.warning("Loader not found for resource: {}", g)
          sender() ! LoadedResources.empty
      }
  }
}
