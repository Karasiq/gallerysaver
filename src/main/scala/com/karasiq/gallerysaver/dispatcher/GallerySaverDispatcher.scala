package com.karasiq.gallerysaver.dispatcher

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.karasiq.gallerysaver.scripting._
import com.karasiq.mapdb.MapDbWrapper.MapDbTreeMap
import com.karasiq.mapdb.serialization.MapDbSerializer
import com.karasiq.mapdb.{MapDbFile, MapDbWrapper}
import org.mapdb.Serializer

import scala.util.{Failure, Success}

class GallerySaverDispatcher(mapDbFile: MapDbFile, fileDownloader: ActorRef, loaders: LoaderRegistry) extends Actor with ActorLogging {
  import context.dispatcher

  override def receive: Receive = {
    case url: String ⇒
      loaders.forUrl(url) match {
        case Some(loader) ⇒
          val sender = this.sender()
          log.info("Fetching URL with {}: {}", loader.id, url)
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
      loader ! f.asFileToDownload
      sender() ! LoadedResources.empty

    case cg: CacheableGallery ⇒
      require(cg.loader.ne(null) && cg.loader.nonEmpty, "Invalid caching ID")

      import MapDbSerializer.Default._
      implicit val resourceSerializer: Serializer[LoadableResource] = javaObjectSerializer

      val cache: MapDbTreeMap[String, LoadedResources] = MapDbWrapper(mapDbFile).createTreeMap(cg.loader)(_
        .keySerializer(MapDbSerializer[String])
        .valueSerializer(MapDbSerializer[LoadedResources])
        .nodeSize(32)
        .valuesOutsideNodesEnable()
      )

      val sender = this.sender()

      cache.get(cg.url) match {
        case Some(resources) ⇒
          log.debug("Found in cache: {}", cg)
          sender ! resources

        case None ⇒
          loaders.forId(cg.loader).orElse(loaders.forUrl(cg.url)) match {
            case Some(loader) ⇒
              log.info("Caching resource: {}", cg)
              loader.load(cg).onComplete {
                case Success(resources) ⇒
                  val result = LoadedResources(resources.toStream)
                  cache += cg.url → result
                  sender ! result

                case Failure(exc) ⇒
                  log.error(exc, "Error loading resource: {}", cg)
                  sender ! LoadedResources.empty
              }

            case None ⇒
              log.warning("Loader not found for resource: {}", cg)
              sender ! LoadedResources.empty
          }
      }

    case g: LoadableGallery ⇒
      loaders.forId(g.loader).orElse(loaders.forUrl(g.url)) match {
        case Some(loader) ⇒
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
          sender ! LoadedResources.empty
      }
  }
}
