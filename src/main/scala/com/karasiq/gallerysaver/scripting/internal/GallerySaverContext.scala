package com.karasiq.gallerysaver.scripting.internal

import javax.script.ScriptEngine

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
  * Gallery saver script context
  * @param config                 Configuration
  * @param mapDbFile              MapDB instance
  * @param executionContext       Execution context
  * @param gallerySaverDispatcher Primary loader dispatcher
  * @param scriptEngine           Scripting engine
  */
final case class GallerySaverContext(config: Config, mapDbFile: MapDbFile, executionContext: ExecutionContext, gallerySaverDispatcher: ActorRef, scriptEngine: ScriptEngine, actorSystem: ActorSystem, registry: LoaderRegistry) {
  val log = Logging(actorSystem, "ScalaScriptEngine")
  val actorMaterializer = ActorMaterializer(ActorMaterializerSettings(actorSystem))(actorSystem)
}
