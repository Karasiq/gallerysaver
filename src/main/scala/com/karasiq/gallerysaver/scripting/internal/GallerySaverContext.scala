package com.karasiq.gallerysaver.scripting.internal

import javax.script.ScriptEngine

import scala.concurrent.ExecutionContext

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config

import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.mapdb.MapDbFile

/**
  * Gallery saver script context
  * @param config                 Configuration
  * @param mapDbFile              MapDB instance
  * @param executionContext       Execution context
  * @param gallerySaverDispatcher Primary loader dispatcher
  * @param scriptEngine           Scripting engine
  */
final case class GallerySaverContext(config: Config, mapDbFile: MapDbFile, executionContext: ExecutionContext,
                                     gallerySaverDispatcher: ActorRef, scriptEngine: ScriptEngine,
                                     actorSystem: ActorSystem, registry: LoaderRegistry) {

  val log = Logging(actorSystem, "GallerySaver")
  implicit val materializer: Materializer = ActorMaterializer()(actorSystem)
}
