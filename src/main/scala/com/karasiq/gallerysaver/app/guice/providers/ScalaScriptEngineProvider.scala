package com.karasiq.gallerysaver.app.guice.providers

import javax.script.{ScriptEngine, ScriptEngineManager}

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.scripting.{LoaderRegistry, LoaderUtils, ScriptExecutor}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class ScalaScriptEngineProvider @Inject()(config: Config, @Named("gallerySaverDispatcher") gallerySaverDispatcher: ActorRef, registry: LoaderRegistry, actorSystem: ActorSystem, executionContext: ExecutionContext) extends Provider[ScriptEngine] {
  override def get(): ScriptEngine = {
    new ScriptEngineManager().getEngineByName("scala") match {
      case scalaInterpreter: scala.tools.nsc.interpreter.IMain ⇒
        scalaInterpreter.settings.embeddedDefaults[this.type]
        scalaInterpreter.settings.usejavacp.value = true
        scalaInterpreter.bind("Loaders", registry)
        scalaInterpreter.bind("Dispatcher", gallerySaverDispatcher)
        scalaInterpreter.bind("Akka", actorSystem)
        scalaInterpreter.bind("LoaderUtils", new LoaderUtils(executionContext, gallerySaverDispatcher))
        scalaInterpreter.bind("LoaderPool", executionContext)
        scalaInterpreter.bind("Scripts", new ScriptExecutor(scalaInterpreter))
        scalaInterpreter.bind("Config", config)
        scalaInterpreter.bind("Log", Logging(actorSystem, "ScalaScriptEngine"))
        scalaInterpreter

      case _ ⇒
        throw new IllegalArgumentException("Invalid scala interpreter")
    }
  }
}
