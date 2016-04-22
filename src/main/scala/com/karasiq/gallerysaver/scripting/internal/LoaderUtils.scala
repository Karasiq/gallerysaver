package com.karasiq.gallerysaver.scripting.internal

import java.net.{URL, URLEncoder}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.karasiq.gallerysaver.builtin.{ImageHostingResource, PreviewsResource}
import com.karasiq.gallerysaver.dispatcher.LoadedResources
import com.karasiq.gallerysaver.imageconverter.FileDownloaderImageConverter
import com.karasiq.gallerysaver.mapdb.FileDownloaderHistory
import com.karasiq.gallerysaver.scripting.resources.{InfiniteGallery, LoadableFile, LoadableResource}
import com.typesafe.config.Config

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Loader scripting helper
  */
object LoaderUtils {
  implicit def executionContext(implicit ctx: GallerySaverContext): ExecutionContext = {
    ctx.executionContext
  }

  implicit def actorSystem(implicit ctx: GallerySaverContext): ActorSystem = {
    ctx.actorSystem
  }

  implicit def actorMaterializer(implicit ctx: GallerySaverContext): ActorMaterializer = {
    ctx.actorMaterializer
  }

  def log(implicit ctx: GallerySaverContext): LoggingAdapter = {
    ctx.log
  }

  def dispatcher(implicit ctx: GallerySaverContext): ActorRef = {
    ctx.gallerySaverDispatcher
  }

  def config(implicit ctx: GallerySaverContext): Config = {
    ctx.config
  }

  /**
    * Performs full traverse and extracts all available files
    * @param resources Resources
    * @return Iterator of all available files
    */
  def filesSource(resources: Source[LoadableResource, akka.NotUsed])(implicit ctx: GallerySaverContext): Source[LoadableFile, akka.NotUsed] = {
    val sources = resources.collect {
      case lf: LoadableFile ⇒
        Source.single(lf)

      case ig: InfiniteGallery ⇒
        Source.fromFuture(get(ig))
          .flatMapConcat(r ⇒ filesSource(r.resources))
          .takeWithin(3 minutes)

      case lr: LoadableResource ⇒
        Source.fromFuture(get(lr))
          .flatMapConcat(r ⇒ filesSource(r.resources))
    }
    sources.flatMapMerge(4, identity)
  }

  /**
    * Performs full traverse and extracts all available files
    * @param resources Resources
    * @return Iterator of all available files
    */
  def extractAllFiles(resources: LoadableResource*)(implicit ctx: GallerySaverContext): Source[LoadableFile, akka.NotUsed] = {
    filesSource(Source(resources.toVector))
  }

  /**
    * Downloads provided file
    * @param file Loadable file
    */
  def download(file: LoadableFile)(implicit ctx: GallerySaverContext): Unit = {
    ctx.gallerySaverDispatcher ! file
  }

  /**
    * Performs full traverse and downloads all available files
    * @param resources Resources
    * @note Infinite galleries not supported, use [[com.karasiq.gallerysaver.scripting.internal.LoaderUtils#extractAllFiles extractAllFiles]] instead
    */
  def loadAllResources(resources: LoadableResource*)(implicit ctx: GallerySaverContext): Unit = {
    load(Source(resources.toVector))
  }

  /**
    * Performs full traverse and downloads all available files
    * @param urls Resource URLs
    * @note Infinite galleries not supported, use [[com.karasiq.gallerysaver.scripting.internal.LoaderUtils#extractAllFiles extractAllFiles]] instead
    */
  def loadAllUrls(urls: String*)(implicit ctx: GallerySaverContext): Unit = {
    urls.foreach { url ⇒
      get(url).foreach {
        case LoadedResources(resources) ⇒
          load(resources)
      }
    }
  }

  /**
    * Creates future from provided function, using default [[scala.concurrent.ExecutionContext execution context]]
    * @param f Function
    * @tparam T Result type
    * @return [[scala.concurrent.Future Future]] of specified result type
    */
  def future[T](f: ⇒ T)(implicit ctx: GallerySaverContext): Future[T] = {
    Future(f)(ctx.executionContext)
  }

  /**
    * Synchronously fetches provided resource URL
    * @param url Resource URL
    * @return Fetched resources
    */
  def getSync(url: String)(implicit ctx: GallerySaverContext): LoadedResources = {
    await(get(url))
  }

  /**
    * Synchronously fetches provided resource
    * @param resource Resource descriptor
    * @return Fetched resources
    */
  def getSync(resource: LoadableResource)(implicit ctx: GallerySaverContext): LoadedResources = {
    await(get(resource))
  }

  /**
    * Awaits result of future, using predefined timeout
    * @param future Future
    * @tparam T Future result type
    * @return Future result
    * @throws TimeoutException if after waiting for the specified time `Future` is still not ready
    */
  @throws[TimeoutException]
  def await[T](future: Future[T])(implicit ctx: GallerySaverContext): T = {
    Await.result(future, timeout.duration)
  }

  private implicit def timeout(implicit ctx: GallerySaverContext): Timeout = {
    Timeout(ctx.config.getDuration("gallery-saver.future-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  }

  /**
    * Asynchronously fetches provided resource
    * @param url Resource URL
    * @return Fetched resources
    */
  def asSource(url: String)(implicit ctx: GallerySaverContext): Source[LoadableResource, akka.NotUsed] = {
    Source.fromFuture(get(url)).flatMapConcat(_.resources)
  }

  /**
    * Asynchronously fetches provided URL and then fetches available sub-resources
    * @param url Resource URL
    * @return Future of fetched sub-resources
    */
  def traverse(url: String)(implicit ctx: GallerySaverContext): Source[LoadableResource, akka.NotUsed] = {
    Source.fromFuture(get(url).map(r ⇒ r.resources.mapAsync(1)(get).flatMapConcat(_.resources))).flatMapConcat(identity)
  }

  /**
    * Asynchronously fetches provided URL
    * @param url Resource URL
    * @return Future of fetched resources
    */
  def get(url: String)(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    (ctx.gallerySaverDispatcher ? url).mapTo[LoadedResources]
  }

  /**
    * Provides tag for specified gallery name
    * @param name Gallery name
    * @return Tag or `unsorted`
    */
  def tagFor(name: String)(implicit ctx: GallerySaverContext): String = {
    tagUtil.first(name).getOrElse("unsorted")
  }

  private def tagUtil(implicit ctx: GallerySaverContext): TagUtil = {
    TagUtil(ctx.config.getConfig("gallery-saver.tags"))
  }

  /**
    * Loads generic previews page
    * @param url  Previews page URL
    * @param path Images save path
    * @return Future of available images
    */
  def loadByPreview(url: String, path: Seq[String] = Seq("previews", "unsorted"))(implicit ctx: GallerySaverContext): Source[LoadableResource, akka.NotUsed] = {
    asSource(PreviewsResource(url, path))
  }

  /**
    * Asynchronously fetches provided resource
    * @param resource Resource descriptor
    * @return Fetched resources
    */
  def asSource(resource: LoadableResource)(implicit ctx: GallerySaverContext): Source[LoadableResource, akka.NotUsed] = {
    Source.fromFuture(get(resource)).flatMapConcat(_.resources)
  }

  /**
    * Asynchronously fetches provided resource
    * @param resource Resource descriptor
    * @return Future of fetched resources
    */
  def get(resource: LoadableResource)(implicit ctx: GallerySaverContext): Future[LoadedResources] = {
    (ctx.gallerySaverDispatcher ? resource).mapTo[LoadedResources]
  }

  /**
    * Loads image hosting page
    * @param url  Image hosting page URL
    * @param path Images save path
    * @return Future of available images
    */
  def loadImageHosting(url: String, path: Seq[String] = Seq("imagehosting", "unsorted"))(implicit ctx: GallerySaverContext): Source[LoadableResource, akka.NotUsed] = {
    asSource(ImageHostingResource(url, path))
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

  private def load(resources: Source[LoadableResource, akka.NotUsed])(implicit ctx: GallerySaverContext): Unit = {
    filesSource(resources).runForeach(ctx.gallerySaverDispatcher ! _)
  }

  class ContextBindings(implicit ctx: GallerySaverContext) {
    final val config = ctx.config
    final val dispatcher = ctx.gallerySaverDispatcher
    final val actorSystem = ctx.actorSystem
    final val log = ctx.log
    final implicit val executionContext = ctx.executionContext
  }
}
