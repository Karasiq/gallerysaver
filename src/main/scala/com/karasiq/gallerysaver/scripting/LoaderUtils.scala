package com.karasiq.gallerysaver.scripting

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.karasiq.gallerysaver.dispatcher.LoadedResources

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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

  def traverse(url: String): Future[LoadedResources] = {
    get(url).flatMap { r ⇒
      Future.sequence(r.resources.map(get)).map { r ⇒
        LoadedResources(r.flatMap(_.resources))
      }
    }
  }
}
