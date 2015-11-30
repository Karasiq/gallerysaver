package com.karasiq.gallerysaver.app.guice

import javax.script.{ScriptEngine, ScriptEngineManager}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.scripting.{LoaderRegistry, LoaderUtils}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class ScalaScriptEngineProvider @Inject()(@Named("gallerySaverDispatcher") gallerySaverDispatcher: ActorRef, registry: LoaderRegistry, actorSystem: ActorSystem, executionContext: ExecutionContext) extends Provider[ScriptEngine] {
  private val engine = new ScriptEngineManager().getEngineByName("scala") match {
    case scalaInterpreter: scala.tools.nsc.interpreter.IMain ⇒
      scalaInterpreter.settings.embeddedDefaults[this.type]
      scalaInterpreter.settings.usejavacp.value = true
      scalaInterpreter.bind("Loaders", registry)
      scalaInterpreter.bind("Dispatcher", gallerySaverDispatcher)
      scalaInterpreter.bind("Akka", actorSystem)
      scalaInterpreter.bind("LoaderUtils", new LoaderUtils(actorSystem, executionContext, gallerySaverDispatcher))
      scalaInterpreter.bind("LoaderPool", executionContext)
      scalaInterpreter

    case e: ScriptEngine ⇒
      e
  }

  override def get(): ScriptEngine = {
    engine
  }
}
