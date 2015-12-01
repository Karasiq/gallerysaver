package com.karasiq.gallerysaver.dispatcher

import java.net.URL
import java.nio.file.{Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.karasiq.gallerysaver.scripting._
import com.karasiq.mapdb.MapDbWrapper.MapDbTreeMap
import com.karasiq.mapdb.serialization.MapDbSerializer
import com.karasiq.mapdb.{MapDbFile, MapDbWrapper}
import com.karasiq.networkutils.downloader.{FileDownloader, FileToDownload}
import org.apache.http.impl.cookie.BasicClientCookie
import org.mapdb.Serializer

import scala.util.{Failure, Success}

object GallerySaverDispatcher {
  def asFileToDownload(directory: Path, f: LoadableFile): FileToDownload = {
    val path = Paths.get(directory.toString, f.hierarchy:_*)
    val host = new URL(f.url).getHost
    val expire = Date.from(Instant.now().plus(30, ChronoUnit.DAYS))
    val cookies = f.cookies.map { case (k, v) ⇒
      val cookie = new BasicClientCookie(k, v)
      cookie.setDomain(host)
      cookie.setPath("/")
      cookie.setSecure(f.url.startsWith("https://"))
      cookie.setExpiryDate(expire)
      cookie
    }
    FileToDownload(f.url, path.toString, f.fileName.getOrElse(""), f.referrer.map(FileDownloader.referer(_)).toList, cookies, sendReport = false)
  }
}

class GallerySaverDispatcher(rootDirectory: Path, mapDbFile: MapDbFile, fileDownloader: ActorRef, loaders: LoaderRegistry) extends Actor with ActorLogging {
  import context.dispatcher

  private def loadCached(loader: GalleryLoader, cg: CacheableGallery): Unit = {
    val sender = this.sender()

    import MapDbSerializer.Default._
    implicit def resourceSerializer: Serializer[LoadableResource] = javaObjectSerializer

    val cache: MapDbTreeMap[String, LoadedResources] = MapDbWrapper(mapDbFile).createTreeMap(cg.loader)(_
      .keySerializer(MapDbSerializer[String])
      .valueSerializer(MapDbSerializer[LoadedResources])
      .nodeSize(32)
      .valuesOutsideNodesEnable()
    )

    cache.get(cg.url) match {
      case Some(resources) ⇒
        log.debug("Found in cache: {}", cg)
        sender ! resources

      case None ⇒
        log.debug("Caching resource: {}", cg)
        loader.load(cg).onComplete {
          case Success(resources) ⇒
            val result = LoadedResources(resources.toStream)
            if (result.resources.isEmpty) {
              log.warning(s"No resources found for: $cg")
            } else {
              cache += cg.url → result
            }
            sender ! result

          case Failure(exc) ⇒
            log.error(exc, "Error loading resource: {}", cg)
            sender ! LoadedResources.empty
        }
    }
  }

  private def loadResource(loader: GalleryLoader, g: LoadableGallery): Unit = {
    val sender = this.sender()

    log.debug("Loading resource: {}", g)
    loader.load(g).onComplete {
      case Success(resources) ⇒
        if (resources.isEmpty) log.warning(s"No resources found for: $g")
        sender ! LoadedResources(resources.toStream)

      case Failure(exc) ⇒
        log.error(exc, "Error loading resource: {}", g)
        sender ! LoadedResources.empty
    }
  }

  override def receive: Receive = {
    case url: String ⇒
      loaders.forUrl(url) match {
        case Some(loader) ⇒
          val sender = this.sender()
          log.debug("Fetching URL with {}: {}", loader.id, url)
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
      val loader = loaders.forId(f.loader)
        .flatMap(_.fileDownloader)
        .getOrElse(fileDownloader)
      log.debug("Loading file: {}", f)
      loader ! GallerySaverDispatcher.asFileToDownload(rootDirectory, f)
      sender() ! LoadedResources.empty

    case cg: CacheableGallery ⇒
      val sender = this.sender()
      loaders.forId(cg.loader).orElse(loaders.forUrl(cg.url)) match {
        case Some(loader) ⇒
          loadCached(loader, cg)

        case None ⇒
          log.warning("No loader found for: {}", cg)
          sender ! LoadedResources.empty
      }

    case g: LoadableGallery ⇒
      val sender = this.sender()
      loaders.forId(g.loader).orElse(loaders.forUrl(g.url)) match {
        case Some(loader) ⇒
          loadResource(loader, g)

        case None ⇒
          log.warning("Loader not found for resource: {}", g)
          sender ! LoadedResources.empty
      }
  }
}
