package com.karasiq.gallerysaver.dispatcher

import java.io.{FileOutputStream, IOException}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.karasiq.gallerysaver.mapdb.GalleryCacheStore
import com.karasiq.gallerysaver.scripting.loaders.GalleryLoader
import com.karasiq.gallerysaver.scripting.resources._
import com.karasiq.networkutils.downloader.{FileDownloader, FileToDownload}
import org.apache.commons.io.IOUtils
import org.apache.http.impl.cookie.BasicClientCookie

import scala.language.postfixOps
import scala.util.control.Exception
import scala.util.{Failure, Success, Try}

object GallerySaverDispatcher {
  /**
    * Converts [[com.karasiq.gallerysaver.scripting.resources.LoadableFile LoadableFile]] to [[com.karasiq.networkutils.downloader.FileToDownload FileToDownload]]
    *
    * @param directory Destination directory
    * @param f         File resource descriptor
    * @return File downloader actor structure
    * @see [[com.karasiq.networkutils.downloader.FileDownloaderActor FileDownloaderActor]]
    */
  def asFileToDownload(directory: Path, f: LoadableFile): FileToDownload = {
    val path = Paths.get(directory.toString, f.hierarchy: _*)
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

  /**
    * Updates cached resources
    *
    * @param r   Resources to patch
    * @param res Parent resource
    */
  def patchResources(r: Seq[LoadableResource], res: LoadableResource): Seq[LoadableResource] = {
    def newHierarchy(h1: Seq[String], h2: Seq[String]): Seq[String] = {
      h1 ++ h2.drop(h1.length)
    }

    r.collect {
      case f: LoadableFile ⇒
        FileResource(f.loader, f.url, f.referrer, f.cookies ++ res.cookies, newHierarchy(res.hierarchy, f.hierarchy), f.fileName)

      case cg: CacheableGallery ⇒
        CachedGalleryResource(cg.loader, cg.url, cg.referrer, cg.cookies ++ res.cookies, newHierarchy(res.hierarchy, cg.hierarchy))

      case g: LoadableGallery ⇒
        GalleryResource(g.loader, g.url, g.referrer, g.cookies ++ res.cookies, newHierarchy(res.hierarchy, g.hierarchy))
    }
  }
}

/**
  * Main resource loading dispatcher
  *
  * @param rootDirectory  Destination directory
  * @param galleryCache   Cache store
  * @param fileDownloader File downloader actor
  * @param loaders        Loaders registry
  */
class GallerySaverDispatcher(rootDirectory: Path, galleryCache: GalleryCacheStore, fileDownloader: ActorRef, loaders: LoaderRegistry) extends Actor with ActorLogging {

  import context.dispatcher

  final implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  override def receive: Receive = {
    case url: String ⇒
      loaders.forUrl(url) match {
        case Some(loader) ⇒
          val sender = this.sender()
          log.debug("Fetching URL with {}: {}", loader.id, url)
          sender ! LoadedResources(loader.load(url))

        case None ⇒
          log.warning("Loader not found for URL: {}", url)
          sender() ! None
      }

    case fg: FileGenerator ⇒
      log.debug("Generating file: {}", fg)
      val path = Paths.get(rootDirectory.toString, fg.hierarchy: _*)
        .resolve(FileDownloader.fileNameFor(fg.url, fg.fileName.getOrElse("")))

      Files.createDirectories(path.getParent)
      val outputStream = new FileOutputStream(path.toFile)
      Exception.allCatch.andFinally(IOUtils.closeQuietly(outputStream)) {
        fg.write(outputStream)
      }
      sender() ! LoadedResources.empty

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

  private def loadCached(loader: GalleryLoader, cg: CacheableGallery): Unit = {
    val sender = this.sender()

    Try(galleryCache.get(cg.url)) match {
      case Success(Some(resources)) ⇒
        log.debug("Found in cache: {}", cg)
        sender ! LoadedResources(Source(GallerySaverDispatcher.patchResources(resources, cg).toVector))

      case _ ⇒
        log.debug("Caching resource: {}", cg)
        loader.load(cg).runWith(Sink.seq).onComplete {
          case Success(resources) ⇒
            val result = LoadedResources(Source(resources))
            if (resources.isEmpty) {
              log.warning(s"No resources found for: $cg")
            } else {
              galleryCache += cg.url → resources
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
    sender ! LoadedResources(loader.load(g))
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3) {
    case _: IOException ⇒
      Restart
  }
}
