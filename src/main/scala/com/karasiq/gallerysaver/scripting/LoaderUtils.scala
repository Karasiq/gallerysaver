package com.karasiq.gallerysaver.scripting

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.karasiq.gallerysaver.builtin.PreviewsResource
import com.karasiq.gallerysaver.dispatcher.LoadedResources

import scala.collection.GenTraversableOnce
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

// Used only in scripts
final class LoaderUtils(actorSystem: ActorSystem, executionContext: ExecutionContext, gallerySaverDispatcher: ActorRef) {
  import actorSystem.dispatcher
  import akka.pattern.ask

  private implicit val timeout = Timeout(5 minutes)

  def threadPool(): ExecutionContext = executionContext

  def loadAllFiles(resource: LoadableResource): Unit = {
    get(resource).foreach {
      case LoadedResources(resources) ⇒
        resources.foreach(this.loadAllFiles)
    }
  }

  def loadAllFiles(url: String): Unit = {
    get(url).foreach {
      case LoadedResources(resources) ⇒
        resources.foreach(this.loadAllFiles)
    }
  }

  def future[T](f: ⇒ T): Future[T] = {
    Future(f)(this.threadPool())
  }

  def get(url: String): Future[LoadedResources] = {
    (gallerySaverDispatcher ? url).mapTo[LoadedResources]
  }

  def get(resource: LoadableResource): Future[LoadedResources] = {
    (gallerySaverDispatcher ? resource).mapTo[LoadedResources]
  }

  def asResourcesStream(futures: GenTraversableOnce[Future[LoadedResources]]): LoadedResources = {
    LoadedResources(futures.toStream.flatMap { future ⇒
      Try(Await.result(future, timeout.duration)) match {
        case Success(LoadedResources(resources)) ⇒
          resources

        case Failure(_) ⇒
          Nil
      }
    })
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
}
