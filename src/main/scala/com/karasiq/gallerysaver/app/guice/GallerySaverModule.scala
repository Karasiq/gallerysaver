package com.karasiq.gallerysaver.app.guice

import java.util.concurrent.Executors
import javax.script.ScriptEngine

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.ExecutionContext

class GallerySaverModule extends AbstractModule with ScalaModule {
  private val executionContext = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(40))

  override def configure(): Unit = {
    bind[ExecutionContext].toInstance(executionContext)
    bind[LoaderRegistry].toProvider[LoaderRegistryProvider]
    bind[ScriptEngine].annotatedWithName("scala").toProvider[ScalaScriptEngineProvider]
    bind[ActorRef].annotatedWithName("fileDownloader").toProvider[FileDownloaderProvider]
    bind[ActorRef].annotatedWithName("gallerySaverDispatcher").toProvider[GallerySaverDispatcherProvider]
  }
}
