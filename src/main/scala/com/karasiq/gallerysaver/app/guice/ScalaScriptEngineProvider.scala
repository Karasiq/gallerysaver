package com.karasiq.gallerysaver.app.guice

import javax.script.{ScriptEngine, ScriptEngineManager}

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.dispatcher.LoadedResources
import com.karasiq.gallerysaver.scripting.{LoadableResource, LoaderRegistry}

import scala.concurrent.duration._

class ScalaScriptEngineProvider @Inject()(@Named("gallerySaverDispatcher") gallerySaverDispatcher: ActorRef, registry: LoaderRegistry, actorSystem: ActorSystem) extends Provider[ScriptEngine] {
  object LoaderUtils {
    import actorSystem.dispatcher
    import akka.pattern.ask

    private implicit val timeout = Timeout(5 minutes)

    def loadAllFiles(resource: LoadableResource): Unit = {
      (gallerySaverDispatcher ? resource).foreach {
        case LoadedResources(resources) ⇒
          resources.foreach(this.loadAllFiles)
      }
    }

    def loadAllFiles(url: String): Unit = {
      (gallerySaverDispatcher ? url).foreach {
        case LoadedResources(resources) ⇒
          resources.foreach(this.loadAllFiles)
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
