package com.karasiq.gallerysaver.app

import java.io.{Closeable, File, FileWriter, PrintWriter, Writer}
import java.nio.file.{Path, Paths}
import java.util
import java.util.logging.Level

import akka.actor.ActorSystem
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.fileutils.PathUtils._
import com.karasiq.fileutils.pathtree.PathTreeUtils._
import com.karasiq.gallerysaver.app.guice.{GallerySaverMainModule, GallerySaverModule}
import com.karasiq.gallerysaver.mapdb.AppSQLContext
import com.karasiq.gallerysaver.scripting.internal.{GallerySaverContext, LoaderUtils}
import com.karasiq.networkutils.HtmlUnitUtils
import javax.script.{ScriptEngine, SimpleScriptContext}
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.commons.io.IOUtils
import org.apache.commons.logging.LogFactory
import org.jline.mod.Nano
import org.jline.terminal.TerminalBuilder

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.{Source, StdIn}
import scala.language.postfixOps
import scala.util.control.{Exception, NonFatal}
import scala.util.{Failure, Success, Try}

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
    loadScripts(engine, scripts.toSeq: _*)
  }

  private def startup(): Unit = {
    // Disable HtmlUnit logging
    sys.props(org.apache.commons.logging.impl.LogFactoryImpl.LOG_PROPERTY) = "org.apache.commons.logging.impl.NoOpLog"
    LogFactory.getFactory.setAttribute(org.apache.commons.logging.impl.LogFactoryImpl.LOG_PROPERTY, "org.apache.commons.logging.impl.NoOpLog")
    java.util.logging.Logger.getGlobal.setLevel(Level.OFF)
    HtmlUnitUtils.disableLogging()

    // Dependency injector
    val injector = Guice.createInjector(new GallerySaverMainModule, new GallerySaverModule)
    val cl = Thread.currentThread().getContextClassLoader

    // Add shutdown hook
    sys.addShutdownHook {
      val actorSystem = injector.instance[ActorSystem]
      actorSystem.log.info("Shutting down GallerySaver")
      Await.result(actorSystem.terminate(), Duration.Inf)

      val storage = injector.instance[AppSQLContext]
      storage match {
        case c: Closeable => c.close()
        case _ => // Ignore
      }
    }

    // Load scripts
    implicit val context = injector.instance[GallerySaverContext]
    val engine = injector.instance[ScriptEngine](Names.named("scala"))
    // Thread.currentThread().setContextClassLoader(cl) // Fix scala compiler buggy loader

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
    val consoleContext = new SimpleScriptContext()
    val dummyWriter = new Writer() {
      override def write(cbuf: Array[Char], off: Int, len: Int): Unit = ()

      override def flush(): Unit = ()

      override def close(): Unit = ()
    }

    consoleContext.setWriter(dummyWriter)
    consoleContext.setErrorWriter(dummyWriter)

    val imports = Seq("com.karasiq.gallerysaver.scripting.loaders._", "com.karasiq.gallerysaver.scripting.resources._")
    imports.foreach(imp ⇒ engine.eval(s"import $imp", consoleContext))
    println("--- REPL initialized. Awaiting input ---")

    val terminal = TerminalBuilder.builder().build()

    val nano = new Nano(terminal, new File(".")) {

      import scala.collection.JavaConverters._

      override protected def doExecute(lines: util.List[String]): Boolean = {
        Try {
          val fw = new FileWriter(new File("gs-history.log"), true)
          val pw = new PrintWriter(fw)
          lines.asScala.foreach(pw.println)
          pw.close()
        }

        val links = lines.asScala.forall(line => line.startsWith("https://") || line.startsWith("http://"))
        if (links) {
          val failure = Try(LoaderUtils.loadAllUrls(lines.asScala.toVector: _*)).failed.toOption.map(_.toString)
          failure.foreach(setMessage)
          failure.isEmpty
        } else {
          Try(engine.eval(lines.asScala.mkString("\n"), consoleContext)) match {
            case Success(value) ⇒
              if (value == null) setMessage(null)
              else {
                val string = value.toString
                Try {
                  val fw = new FileWriter(new File("gs-output.log"), true)
                  fw.write(string)
                  fw.write("\n")
                  fw.close()
                }
                setMessage(string)
              }
              true
            case Failure(exc) ⇒
              Try {
                val fw = new FileWriter(new File("gs-traces.log"), true)
                val pw = new PrintWriter(fw)
                exc.printStackTrace(pw)
                pw.close()
              }

              setMessage(exc.toString)
              false
          }
        }
      }
    }
    try {
      AppLogger.println = (_, v) => nano.setMessageInstant(v.toString)
      nano.run()
    } catch {
      case NonFatal(_) =>
        AppLogger.println = (s, _) => scala.Predef.println(s)
        Iterator.continually(StdIn.readLine()).takeWhile(_.ne(null)).foreach { line ⇒
          if (line.startsWith("https://") || line.startsWith("http://")) {
            Try(LoaderUtils.loadAllUrls(line)).failed.foreach(System.err.println(_))
          } else {
            Try(engine.eval(line, consoleContext)) match {
              case Success(value) ⇒ if (value.ne(null)) println(value)
              case Failure(exc) ⇒ exc.printStackTrace()
            }
          }
        }
    }
    sys.exit(0)
  }

  startup()
}
