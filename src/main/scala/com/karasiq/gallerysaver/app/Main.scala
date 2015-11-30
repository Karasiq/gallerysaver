package com.karasiq.gallerysaver.app

import java.io.{FileInputStream, InputStreamReader}
import java.nio.file.{Path, Paths}
import javax.script.{ScriptEngine, SimpleScriptContext}

import akka.actor.ActorSystem
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.fileutils.PathUtils._
import com.karasiq.fileutils.pathtree.PathTreeUtils._
import com.karasiq.gallerysaver.app.guice.{GallerySaverMainModule, GallerySaverModule}
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import com.karasiq.mapdb.MapDbFile
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.commons.io.IOUtils

import scala.io.StdIn
import scala.util.control.Exception
import scala.util.{Failure, Success, Try}

object Main extends App {
  private val injector = Guice.createInjector(new GallerySaverMainModule, new GallerySaverModule)

  def startup(): Unit = {
    val engine = injector.instance[ScriptEngine](Names.named("scala"))
    val registry = injector.instance[LoaderRegistry]

    def loadScriptsDirectory(dir: Path): Unit = {
      val scripts = dir.subFiles.collect {
        case file if file.getFileName.toString.endsWith(".scala") ⇒
          println(s"Loading plugin: $file")
          file
      }

      scripts.foreach { sc ⇒
        val reader = new InputStreamReader(new FileInputStream(sc.toFile), "UTF-8")
        Exception.allCatch.andFinally(IOUtils.closeQuietly(reader)) {
          engine.eval(reader)
        }
      }
    }

    Paths.get("loaders") match {
      case loadersDir if loadersDir.isDirectory ⇒
        loadScriptsDirectory(loadersDir)

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

  def shutdown(): Unit = {
    val actorSystem = injector.instance[ActorSystem]
    actorSystem.log.info("Shutting down GallerySaver")
    actorSystem.registerOnTermination(IOUtils.closeQuietly(injector.instance[MapDbFile]))
    actorSystem.terminate()
  }

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = shutdown()
  }))

  startup()
}
