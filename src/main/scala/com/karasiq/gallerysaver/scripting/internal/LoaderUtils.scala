package com.karasiq.gallerysaver.scripting.internal

import java.net.{URL, URLEncoder}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import com.karasiq.gallerysaver.builtin.{ImageHostingResource, PreviewsResource}
import com.karasiq.gallerysaver.dispatcher.LoadedResources
import com.karasiq.gallerysaver.imageconverter.FileDownloaderImageConverter
import com.karasiq.gallerysaver.mapdb.FileDownloaderHistory
import com.karasiq.gallerysaver.scripting.resources.{InfiniteGallery, LoadableFile, LoadableResource}
import com.typesafe.config.Config

import scala.collection.GenTraversableOnce
import scala.concurrent._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Loader scripting helper
  */
object LoaderUtils {
  private implicit def timeout(implicit ctx: GallerySaverContext) = {
    Timeout(ctx.config.getDuration("gallery-saver.future-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  }

  private def tagUtil(implicit ctx: GallerySaverContext) = {
    TagUtil(ctx.config.getConfig("gallery-saver"))
  }

  implicit def executionContext(implicit ctx: GallerySaverContext): ExecutionContext = {
    ctx.executionContext
  }

  def log(implicit ctx: GallerySaverContext): LoggingAdapter = {
    ctx.log
  }

  def actorSystem(implicit ctx: GallerySaverContext): ActorSystem = {
    ctx.actorSystem
  }

  def dispatcher(implicit ctx: GallerySaverContext): ActorRef = {
    ctx.gallerySaverDispatcher
  }

  def config(implicit ctx: GallerySaverContext): Config = {
    ctx.config
  }

  class ContextBindings(implicit ctx: GallerySaverContext) {
    final val config = ctx.config
    final val dispatcher = ctx.gallerySaverDispatcher
    final val actorSystem = ctx.actorSystem
    final val log = ctx.log
    final implicit val executionContext = ctx.executionContext
  }

  /**
    * Performs full traverse and extracts all available files
    *
    * @param resources Resources
    * @return Iterator of all available files
    */
  def extractAllFiles(resources: LoadableResource*)(implicit ctx: GallerySaverContext): Iterator[LoadableFile] = {
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

  private def load(resources: Iterator[LoadableResource])(implicit ctx: GallerySaverContext): Unit = {
    resources.foreach {
      case ig: InfiniteGallery ⇒
        throw new IllegalArgumentException(s"Couldn't load infinite gallery: $ig")

      case lr: LoadableResource ⇒
        get(lr).foreach {
          case LoadedResources(r) ⇒
            load(r.iterator)
        }
    }
  }

  /**
    * Downloads provided file
    *
    * @param file Loadable file
    */
  def download(file: LoadableFile)(implicit ctx: GallerySaverContext): Unit = {
    ctx.gallerySaverDispatcher ! file
  }

  /**
    * Performs full traverse and downloads all available files
    *
    * @param resources Resources
    * @note Infinite galleries not supported, use [[com.karasiq.gallerysaver.scripting.internal.LoaderUtils#extractAllFiles extractAllFiles]] instead
    */
  def loadAllResources(resources: LoadableResource*)(implicit ctx: GallerySaverContext): Unit = {
    load(resources.iterator)
  }

  /**
    * Performs full traverse and downloads all available files
    *
    * @param urls Resource URLs
    * @note Infinite galleries not supported, use [[com.karasiq.gallerysaver.scripting.internal.LoaderUtils#extractAllFiles extractAllFiles]] instead
    */
  def loadAllUrls(urls: String*)(implicit ctx: GallerySaverContext): Unit = {
    urls.foreach { url ⇒
      get(url).foreach {
        case LoadedResources(r) ⇒
          load(r.iterator)
      }
    }
  }

  /**
    * Wraps single resource as resources future
    *
    * @param f Resource provider function
    * @tparam T Resource type
    * @return Resources iterator future
    */
  def asResourcesFuture[T <: LoadableResource](f: ⇒ T): Future[Iterator[T]] = {
    Future.fromTry(Try(Iterator.single(f)))
  }

  /**
    * Creates future from provided function, using default [[scala.concurrent.ExecutionContext execution context]]
    *
    * @param f Function
    * @tparam T Result type
    * @return [[scala.concurrent.Future Future]] of specified result type
    */
  def future[T](f: ⇒ T)(implicit ctx: GallerySaverContext): Future[T] = {
    Future(f)(this.executionContext)
  }

  /**
    * Awaits result of future, using predefined timeout
    *
    * @param future Future
    * @tparam T Future result type
    * @return Future result
    * @throws TimeoutException if after waiting for the specified time `Future` is still not ready
    */
  @throws[TimeoutException]
  def await[T](future: Future[T])(implicit ctx: GallerySaverContext): T = {
    Await.result(future, timeout.duration)
  }

  /**
    * Asynchronously fetches provided URL
    *
    * @param url Resource URL
    * @return Future of fetched resources
    */
  def get(url: String)(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    (ctx.gallerySaverDispatcher ? url).mapTo[LoadedResources]
  }

  /**
    * Asynchronously fetches provided resource
    *
    * @param resource Resource descriptor
    * @return Future of fetched resources
    */
  def get(resource: LoadableResource)(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    (ctx.gallerySaverDispatcher ? resource).mapTo[LoadedResources]
  }

  /**
    * Wraps futures to single [[com.karasiq.gallerysaver.dispatcher.LoadedResources LoadedResources]] object
    *
    * @param futures Futures of fetched resources
    * @return [[com.karasiq.gallerysaver.dispatcher.LoadedResources LoadedResources]]
    */
  def asResourcesStream(futures: GenTraversableOnce[Future[LoadedResources]])(implicit ctx: GallerySaverContext): LoadedResources = {
    LoadedResources(futures.toStream.flatMap { future ⇒
      Try(await(future)) match {
        case Success(LoadedResources(resources)) ⇒
          resources

        case Failure(_) ⇒
          Nil
      }
    })
  }

  /**
    * Synchronously fetches provided resource URL
    *
    * @param url Resource URL
    * @return Fetched resources
    */
  def getSync(url: String)(implicit ctx: GallerySaverContext): LoadedResources = {
    await(get(url))
  }

  /**
    * Synchronously fetches provided resource
    *
    * @param resource Resource descriptor
    * @return Fetched resources
    */
  def getSync(resource: LoadableResource)(implicit ctx: GallerySaverContext): LoadedResources = {
    await(get(resource))
  }

  /**
    * Asynchronously fetches provided URL and then fetches available sub-resources
    *
    * @param url Resource URL
    * @return Future of fetched sub-resources
    */
  def traverse(url: String)(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    get(url).map(r ⇒ asResourcesStream(r.resources.map(get)))
  }

  /**
    * Provides tag for specified gallery name
    *
    * @param name Gallery name
    * @return Tag or `unsorted`
    */
  def tagFor(name: String)(implicit ctx: GallerySaverContext): String = {
    tagUtil.first(name).getOrElse("unsorted")
  }

  /**
    * Loads generic previews page
    *
    * @param url Previews page URL
    * @param path Images save path
    * @return Future of available images
    */
  def loadByPreview(url: String, path: Seq[String] = Seq("previews", "unsorted"))(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    get(PreviewsResource(url, path))
  }

  /**
    * Loads image hosting page
    *
    * @param url Image hosting page URL
    * @param path Images save path
    * @return Future of available images
    */
  def loadImageHosting(url: String, path: Seq[String] = Seq("imagehosting", "unsorted"))(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    get(ImageHostingResource(url, path))
  }

  /**
    * Fixes invalid characters in URL
    *
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

  /**
    * File downloader history provider
    */
  def fdHistory(implicit ctx: GallerySaverContext): FileDownloaderHistory = {
    new FileDownloaderHistory(ctx.mapDbFile)
  }

  /**
    * File downloader image converter provider
    */
  def fdConverter(implicit ctx: GallerySaverContext): FileDownloaderImageConverter = {
    FileDownloaderImageConverter.fromConfig(ctx.config.getConfig("gallery-saver.image-converter"))
  }
}
