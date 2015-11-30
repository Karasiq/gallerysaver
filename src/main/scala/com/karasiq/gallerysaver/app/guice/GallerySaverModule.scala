package com.karasiq.gallerysaver.app.guice

import javax.script.ScriptEngine

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Singleton}
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import net.codingwell.scalaguice.ScalaModule

class GallerySaverModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver"))
    bind[LoaderRegistry].to[LoaderRegistryImpl].in[Singleton]
    bind[ScriptEngine].annotatedWithName("scala").toProvider[ScalaScriptEngineProvider]
    bind[ActorRef].annotatedWithName("fileDownloader").toProvider[FileDownloaderProvider]
    bind[ActorRef].annotatedWithName("gallerySaverDispatcher").toProvider[GallerySaverDispatcherProvider]
  }
}
