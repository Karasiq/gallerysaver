package com.karasiq.gallerysaver.app.guice

import java.nio.file.Paths
import java.util.concurrent.Executors
import javax.script.ScriptEngine

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.AbstractModule
import com.karasiq.fileutils.PathUtils._
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.ExecutionContext

class GallerySaverModule extends AbstractModule with ScalaModule {
  private val config = {
    val defaultConfig = ConfigFactory.load()
    val fileName = defaultConfig.getString("gallery-saver.external-config")

    Paths.get(fileName) match {
      case file if file.isRegularFile ⇒
        ConfigFactory.parseFile(file.toFile)
          .withFallback(defaultConfig)
          .resolve()

      case _ ⇒
        defaultConfig
    }
  }

  private val executionContext = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(40))

  override def configure(): Unit = {
    bind[Config].toInstance(config)
    bind[MapDbFile].toProvider[GallerySaverMapDbProvider]
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver", config))
    bind[ExecutionContext].toInstance(executionContext)
    bind[LoaderRegistry].toProvider[LoaderRegistryProvider]
    bind[ScriptEngine].annotatedWithName("scala").toProvider[ScalaScriptEngineProvider]
    bind[ActorRef].annotatedWithName("fileDownloader").toProvider[FileDownloaderProvider]
    bind[ActorRef].annotatedWithName("gallerySaverDispatcher").toProvider[GallerySaverDispatcherProvider]
  }
}
