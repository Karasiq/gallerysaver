package com.karasiq.gallerysaver.scripting.internal

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.{ActorMaterializer, Materializer}
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.gallerysaver.mapdb.AppSQLContext
import com.typesafe.config.Config
import javax.script.ScriptEngine

import scala.concurrent.ExecutionContext

/**
  * Gallery saver script context
  *
  * @param config                 Configuration
  * @param sqlContext             App SQL context
  * @param executionContext       Execution context
  * @param gallerySaverDispatcher Primary loader dispatcher
  * @param scriptEngine           Scripting engine
  */
final case class GallerySaverContext(config: Config, sqlContext: AppSQLContext, executionContext: ExecutionContext,
                                     gallerySaverDispatcher: ActorRef, scriptEngine: ScriptEngine,
                                     actorSystem: ActorSystem, registry: LoaderRegistry) {

  val log = Logging(actorSystem, "GallerySaver")
  implicit val materializer: Materializer = ActorMaterializer()(actorSystem)
}
