package com.karasiq.gallerysaver.app

import javax.script.{ScriptEngine, SimpleScriptContext}
import java.nio.file.{Path, Paths}

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.{Source, StdIn}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception

import akka.actor.ActorSystem
import com.google.inject.Guice
import com.google.inject.name.Names
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.commons.io.IOUtils

import com.karasiq.fileutils.PathUtils._
import com.karasiq.fileutils.pathtree.PathTreeUtils._
import com.karasiq.gallerysaver.app.guice.{GallerySaverMainModule, GallerySaverModule}
import com.karasiq.gallerysaver.scripting.internal.{GallerySaverContext, LoaderUtils}
import com.karasiq.mapdb.MapDbFile
import com.karasiq.networkutils.HtmlUnitUtils

object Main extends App {
  private def loadScripts(engine: ScriptEngine, scripts: Path*): Unit = {
    scripts.foreach { sc ⇒
      println(s"Executing script: $sc")
      val source = Source.fromFile(sc.toFile, "UTF-8")
      Exception.allCatch.andFinally(source.close()) {
        engine.eval(source.bufferedReader())
      }
    }
  }

  private def loadScriptsDirectory(engine: ScriptEngine, dir: Path): Unit = {
    val scripts = dir.subFiles.filter(_.getFileName.toString.endsWith(".scala"))
    loadScripts(engine, scripts.toSeq:_*)
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
        Await.result(actorSystem.terminate(), Duration.Inf)
      }
    }))

    // Load scripts
    implicit val context = injector.instance[GallerySaverContext]
    val engine = injector.instance[ScriptEngine](Names.named("scala"))

    context.config.getStringList("gallery-saver.auto-exec-folders").foreach { folder ⇒
      Paths.get(folder) match {
        case loadersDir if loadersDir.isDirectory ⇒
          loadScriptsDirectory(engine, loadersDir)

        case _ ⇒
          println(s"Directory $folder not found")
      }
    }

    Paths.get(context.config.getString("gallery-saver.auto-exec-script")) match {
      case autoExec if autoExec.isRegularFile ⇒
        loadScripts(engine, autoExec)

      case f ⇒
        println(s"$f not found")
    }


    // REPL
    val consoleContext = new SimpleScriptContext
    val imports = Seq("com.karasiq.gallerysaver.scripting.loaders._", "com.karasiq.gallerysaver.scripting.resources._")
    imports.foreach(imp ⇒ engine.eval(s"import $imp", consoleContext))
    println("--- REPL initialized. Awaiting input ---")

    Iterator.continually(StdIn.readLine()).takeWhile(_.ne(null)).foreach { line ⇒
      if (line.startsWith("https://") || line.startsWith("http://")) {
        Try(LoaderUtils.loadAllUrls(line)).failed.foreach(System.err.println(_))
      } else {
        Try(engine.eval(line, consoleContext)) match {
          case Success(value) ⇒ if (value.ne(null)) println(value)
          case Failure(exc) ⇒ System.err.println(exc)
        }
      }
    }
  }

  startup()
}
