package com.karasiq.gallerysaver.dispatcher

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.karasiq.gallerysaver.scripting.{CacheableGallery, LoadableFile, LoadableGallery, LoaderRegistry}

class GallerySaverDispatcher(fileDownloader: ActorRef, loaders: LoaderRegistry) extends Actor with ActorLogging {
  override def receive: Receive = {
    case url: String ⇒
      loaders.forUrl(url) match {
        case Some(loader) ⇒
          sender() ! loader.load(url)

        case None ⇒
          log.warning("Loader not found for URL: {}", url)
          sender() ! None
      }

    case f: LoadableFile ⇒
      val loader = loaders.forId(f.loader).flatMap(_.fileDownloader).getOrElse(fileDownloader)
      loader ! f.asFileToDownload
      sender() ! Iterator.empty

    case cg: CacheableGallery ⇒
      // TODO: cache

    case g: LoadableGallery ⇒
      loaders.forId(g.loader).orElse(loaders.forUrl(g.url)) match {
        case Some(loader) ⇒
          sender() ! loader.load(g)

        case None ⇒
          log.warning("Loader not found for resource: {}", g)
          sender() ! Iterator.empty
      }
  }
}
