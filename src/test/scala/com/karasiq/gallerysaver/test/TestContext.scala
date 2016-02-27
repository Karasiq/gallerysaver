package com.karasiq.gallerysaver.test

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.gallerysaver.app.guice.GallerySaverModule
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

private[test] object TestContext {
  val injector = Guice.createInjector(new GallerySaverTestModule, new GallerySaverModule)

  val actorSystem: ActorSystem = injector.instance[ActorSystem]

  val loader: ActorRef = injector.instance[ActorRef](Names.named("gallerySaverDispatcher"))

  implicit val timeout: Timeout = Timeout(5 minutes)

  implicit val executionContext: ExecutionContext = injector.instance[ActorSystem].dispatcher

  val config: Config = injector.instance[Config].getConfig("gallery-saver.test")

  val mapDbFile: MapDbFile = injector.instance[MapDbFile]

  implicit val context: GallerySaverContext = GallerySaverContext(injector.instance[Config], mapDbFile, executionContext, loader, null, actorSystem, null)

  implicit val materializer = context.actorMaterializer
}
