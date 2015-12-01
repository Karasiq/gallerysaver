package com.karasiq.gallerysaver.app

import java.nio.file.{Path, Paths}
import javax.script.{ScriptEngine, SimpleScriptContext}

import akka.actor.ActorSystem
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.fileutils.PathUtils._
import com.karasiq.fileutils.pathtree.PathTreeUtils._
import com.karasiq.gallerysaver.app.guice.{GallerySaverMainModule, GallerySaverModule}
import com.karasiq.mapdb.MapDbFile
import com.karasiq.networkutils.HtmlUnitUtils
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.commons.io.IOUtils

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.{Source, StdIn}
import scala.language.postfixOps
import scala.util.control.Exception
import scala.util.{Failure, Success, Try}

object Main extends App {
  private def loadScriptsDirectory(engine: ScriptEngine, dir: Path): Unit = {
    val scripts = dir.subFiles.filter(_.getFileName.toString.endsWith(".scala"))

    scripts.foreach { sc ⇒
      println(s"Loading plugin: $sc")
      val source = Source.fromFile(sc.toFile, "UTF-8")
      Exception.allCatch.andFinally(source.close()) {
        engine.eval(source.bufferedReader())
      }
    }
  }

  private def startup(): Unit = {
    // Disable HtmlUnit logging
    HtmlUnitUtils.disableLogging()

    // Dependency injector
    val injector = Guice.createInjector(new GallerySaverMainModule, new GallerySaverModule)

    // Add shutdown hook
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        val actorSystem = injector.instance[ActorSystem]
        val mapDbFile = injector.instance[MapDbFile]
        actorSystem.log.info("Shutting down GallerySaver")
        actorSystem.registerOnTermination(IOUtils.closeQuietly(mapDbFile))
        Await.ready(actorSystem.terminate(), Duration.Inf)
      }
    }))

    // Load scripts
    val engine = injector.instance[ScriptEngine](Names.named("scala"))

    Paths.get("loaders") match {
      case loadersDir if loadersDir.isDirectory ⇒
        loadScriptsDirectory(engine, loadersDir)

      case _ ⇒
        println("Loaders directory not found")
    }

    // REPL
    val consoleContext = new SimpleScriptContext
    Iterator.continually(StdIn.readLine("> ")).takeWhile(_.ne(null)).foreach { line ⇒
      Try(engine.eval(line, consoleContext)) match {
        case Success(value) ⇒
          if (value.ne(null)) {
            println(value)
          }

        case Failure(exc) ⇒
          println(exc)
      }
    }
  }

  startup()
}
