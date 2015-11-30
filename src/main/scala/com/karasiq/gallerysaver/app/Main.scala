package com.karasiq.gallerysaver.app

import java.io.{FileInputStream, InputStreamReader}
import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit
import javax.script.{ScriptEngine, SimpleScriptContext}

import akka.actor.ActorSystem
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.fileutils.PathUtils._
import com.karasiq.fileutils.pathtree.PathTreeUtils._
import com.karasiq.gallerysaver.app.guice.GallerySaverModule
import com.karasiq.gallerysaver.scripting.LoaderRegistry
import com.karasiq.mapdb.MapDbFile
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.commons.io.IOUtils

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.control.Exception
import scala.util.{Failure, Success, Try}

trait MainContext {
  protected final val injector = Guice.createInjector(new GallerySaverModule)
  protected final val engine = injector.instance[ScriptEngine](Names.named("scala"))
  protected final val registry = injector.instance[LoaderRegistry]

  protected def loadScriptsDirectory(dir: Path): Unit = {
    val scripts = dir.subFiles.collect {
      case file if file.getFileName.toString.endsWith(".scala") ⇒
        file
    }

    scripts.foreach { sc ⇒
      val reader = new InputStreamReader(new FileInputStream(sc.toFile))
      Exception.allCatch.andFinally(IOUtils.closeQuietly(reader)) {
        engine.eval(reader)
      }
    }
  }

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      Await.ready(injector.instance[ActorSystem].terminate(), FiniteDuration(5, TimeUnit.MINUTES))
      IOUtils.closeQuietly(injector.instance[MapDbFile])
    }
  }))
}

object Main extends MainContext with App {
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
        println(value)

      case Failure(exc) ⇒
        println(exc)
    }
  }
}
