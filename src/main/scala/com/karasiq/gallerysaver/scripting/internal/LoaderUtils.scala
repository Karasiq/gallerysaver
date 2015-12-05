package com.karasiq.gallerysaver.scripting.internal

import java.net.{URL, URLEncoder}

import akka.actor.ActorRef
import akka.util.Timeout
import com.karasiq.gallerysaver.builtin.PreviewsResource
import com.karasiq.gallerysaver.dispatcher.LoadedResources
import com.karasiq.gallerysaver.imageconverter.FileDownloaderImageConverter
import com.karasiq.gallerysaver.mapdb.FileDownloaderHistory
import com.karasiq.gallerysaver.scripting.resources.{InfiniteGallery, LoadableFile, LoadableResource}
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config

import scala.collection.GenTraversableOnce
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

// Used only in scripts
final class LoaderUtils(config: Config, mapDbFile: MapDbFile, executionContext: ExecutionContext, gallerySaverDispatcher: ActorRef) {
  import akka.pattern.ask

  private implicit def ec = executionContext
  private implicit def timeout = Timeout(5 minutes)

  def threadPool(): ExecutionContext = executionContext

  def extractAllFiles(resources: LoadableResource*): Iterator[LoadableFile] = {
    def extract(resources: Iterator[LoadableResource]): Iterator[LoadableFile] = {
      val futures = resources.map {
        case lf: LoadableFile ⇒
          Future.successful(Iterator.single(lf))

        case lr: LoadableResource ⇒
          get(lr).map(r ⇒ extract(r.resources.iterator))
      }

      futures.flatMap { future ⇒
        Try(await(future)) match {
          case Success(r) ⇒
            r

          case Failure(_) ⇒
            Iterator.empty
        }
      }
    }

    extract(resources.iterator)
  }

  def loadAllResources(resources: LoadableResource*): Unit = {
    resources.foreach {
      case ig: InfiniteGallery ⇒
        throw new IllegalArgumentException(s"Couldn't load infinite gallery: $ig")

      case lr: LoadableResource ⇒
        get(lr).foreach {
          case LoadedResources(r) ⇒
            this.loadAllResources(r:_*)
        }
    }
  }

  def loadAllUrls(urls: String*): Unit = {
    urls.foreach { url ⇒
      get(url).foreach {
        case LoadedResources(r) ⇒
          this.loadAllResources(r:_*)
      }
    }
  }

  def future[T](f: ⇒ T): Future[T] = {
    Future(f)(this.threadPool())
  }

  def await[T](future: Future[T]): T = {
    Await.result(future, timeout.duration)
  }

  def get(url: String): Future[LoadedResources] = {
    (gallerySaverDispatcher ? url).mapTo[LoadedResources]
  }

  def get(resource: LoadableResource): Future[LoadedResources] = {
    (gallerySaverDispatcher ? resource).mapTo[LoadedResources]
  }

  def asResourcesStream(futures: GenTraversableOnce[Future[LoadedResources]]): LoadedResources = {
    LoadedResources(futures.toStream.flatMap { future ⇒
      Try(await(future)) match {
        case Success(LoadedResources(resources)) ⇒
          resources

        case Failure(_) ⇒
          Nil
      }
    })
  }

  // For debug purposes
  def getSync(url: String): LoadedResources = {
    await(get(url))
  }

  def getSync(resource: LoadableResource): LoadedResources = {
    await(get(resource))
  }

  def traverse(url: String): Future[LoadedResources] = {
    get(url).map(r ⇒ asResourcesStream(r.resources.map(get)))
  }

  def loadByPreview(url: String, path: Seq[String]): Future[LoadedResources] = {
    (gallerySaverDispatcher ? PreviewsResource(url, path)).mapTo[LoadedResources]
  }

  def loadImageHosting(url: String, path: Seq[String]): Future[LoadedResources] = {
    (gallerySaverDispatcher ? PreviewsResource(url, path)).mapTo[LoadedResources]
  }

  /**
    * Fixes invalid characters in URL
    * @param url URL
    * @return Fixed URL
    */
  def fixUrl(url: String): String = {
    import com.karasiq.networkutils.url._

    def fixName(s: String): String = {
      URLEncoder.encode(s, "UTF-8")
        .replace("+", "%20")
    }

    val parsedUrl = asURL(url)
    val fixedFile = URLParser(parsedUrl).file.path.split('/').filter(_.nonEmpty).map(fixName).mkString("/", "/", "")
    val query = Option(parsedUrl.getQuery).fold("")("?" + _)
    new URL(parsedUrl.getProtocol, parsedUrl.getHost, fixedFile + query).toString
  }

  lazy val fdHistory: FileDownloaderHistory = {
    new FileDownloaderHistory(mapDbFile)
  }

  lazy val fdConverter: FileDownloaderImageConverter = {
    FileDownloaderImageConverter.fromConfig(config.getConfig("gallery-saver.image-converter"))
  }
}
