package com.karasiq.gallerysaver.app.guice.providers

import javax.script.{ScriptEngine, ScriptEngineManager}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class ScalaScriptEngineProvider @Inject()(mapDbFile: MapDbFile, config: Config, @Named("gallerySaverDispatcher") gallerySaverDispatcher: ActorRef, registry: LoaderRegistry, actorSystem: ActorSystem, executionContext: ExecutionContext) extends Provider[ScriptEngine] {
  override def get(): ScriptEngine = {
    new ScriptEngineManager().getEngineByName("scala") match {
      case scalaInterpreter: scala.tools.nsc.interpreter.IMain ⇒
        scalaInterpreter.settings.embeddedDefaults[this.type]
        scalaInterpreter.settings.usejavacp.value = true
        scalaInterpreter.bind("GallerySaverImplicitScriptingContext", "com.karasiq.gallerysaver.scripting.internal.GallerySaverContext", GallerySaverContext(config, mapDbFile, executionContext, gallerySaverDispatcher, scalaInterpreter, actorSystem, registry), List("implicit"))
        scalaInterpreter.eval("import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils, Scripts}")
        scalaInterpreter

      case _ ⇒
        throw new IllegalArgumentException("Invalid scala interpreter")
    }
  }
}
