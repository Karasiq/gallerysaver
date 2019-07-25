package com.karasiq.gallerysaver.app.guice

import javax.script.ScriptEngine

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import com.google.inject.{AbstractModule, Singleton}
import net.codingwell.scalaguice.ScalaModule
import com.karasiq.gallerysaver.app.guice.providers._
import com.karasiq.gallerysaver.mapdb.{FDHistoryStore, GalleryCacheStore}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext

class GallerySaverModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[ExecutionContext].toProvider[ExecutionContextProvider].in[Singleton]
    bind[GallerySaverContext].toProvider[BaseGallerySaverContextProvider].in[Singleton]
    bind[ScriptEngine].annotatedWithName("scala").toProvider[ScalaScriptEngineProvider].in[Singleton]
    bind[FDHistoryStore].toProvider[FDHistoryStoreProvider].in[Singleton]
    bind[GalleryCacheStore].toProvider[GalleryCacheStoreProvider].in[Singleton]
    bind[ActorRef].annotatedWithName("fileDownloader").toProvider[FileDownloaderProvider].in[Singleton]
  }
}
