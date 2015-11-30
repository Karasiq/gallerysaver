package com.karasiq.gallerysaver.app.guice

import javax.script.{ScriptEngine, ScriptEngineManager}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.scripting.{LoadableResource, LoaderRegistry}

class ScalaScriptEngineProvider @Inject()(@Named("gallerySaverDispatcher") gallerySaverDispatcher: ActorRef, registry: LoaderRegistry, actorSystem: ActorSystem) extends Provider[ScriptEngine] {
  object LoaderUtils {
    import actorSystem.dispatcher
    import akka.pattern.ask

    def loadAllFiles(resource: LoadableResource): Unit = {
      (gallerySaverDispatcher ? resource).foreach {
        case resources: Iterator[LoadableResource] ⇒
          resources.foreach(this.loadAllFiles)
      }
    }

    def loadAllFiles(url: String): Unit = {
      (gallerySaverDispatcher ? url).foreach {
        case Some(resource: LoadableResource) ⇒
          this.loadAllFiles(resource)
      }
    }
  }

  private val engine = new ScriptEngineManager().getEngineByName("scala") match {
    case scalaInterpreter: scala.tools.nsc.interpreter.IMain ⇒
      scalaInterpreter.settings.embeddedDefaults[this.type]
      scalaInterpreter.settings.usejavacp.value = true
      scalaInterpreter.bind("Loaders", registry)
      scalaInterpreter.bind("Dispatcher", gallerySaverDispatcher)
      scalaInterpreter.bind("Akka", actorSystem)
      scalaInterpreter.bind("LoaderUtils", LoaderUtils)
      scalaInterpreter

    case e: ScriptEngine ⇒
      e
  }

  override def get(): ScriptEngine = {
    engine
  }
}
