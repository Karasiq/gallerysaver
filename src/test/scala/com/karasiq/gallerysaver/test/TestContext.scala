package com.karasiq.gallerysaver.test

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.google.inject.Guice
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._

import com.karasiq.gallerysaver.app.guice.GallerySaverModule
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.mapdb.MapDbFile

private[test] object TestContext {
  val injector = Guice.createInjector(new GallerySaverTestModule, new GallerySaverModule)
  implicit val context: GallerySaverContext = injector.instance[GallerySaverContext]
  val actorSystem: ActorSystem = context.actorSystem
  val loader: ActorRef = context.gallerySaverDispatcher
  implicit val timeout: Timeout = Timeout(5 minutes)
  implicit val executionContext = context.executionContext
  val config: Config = context.config.getConfig("gallery-saver.test")
  val mapDbFile: MapDbFile = context.mapDbFile
  implicit val materializer = context.materializer
}
