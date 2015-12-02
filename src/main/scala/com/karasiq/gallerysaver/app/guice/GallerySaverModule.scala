package com.karasiq.gallerysaver.app.guice

import javax.script.ScriptEngine

import akka.actor.ActorRef
import com.google.inject.{AbstractModule, Singleton}
import com.karasiq.gallerysaver.app.guice.providers._
import com.karasiq.gallerysaver.dispatcher.LoaderRegistry
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.ExecutionContext

class GallerySaverModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[ExecutionContext].toProvider[ExecutionContextProvider].in[Singleton]
    bind[LoaderRegistry].toProvider[LoaderRegistryProvider].in[Singleton]
    bind[ScriptEngine].annotatedWithName("scala").toProvider[ScalaScriptEngineProvider].in[Singleton]
    bind[ActorRef].annotatedWithName("fileDownloader").toProvider[FileDownloaderProvider].in[Singleton]
    bind[ActorRef].annotatedWithName("gallerySaverDispatcher").toProvider[GallerySaverDispatcherProvider].in[Singleton]
  }
}
