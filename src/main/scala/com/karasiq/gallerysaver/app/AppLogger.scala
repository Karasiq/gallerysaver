package com.karasiq.gallerysaver.app

import java.io.{FileWriter, PrintWriter}

import akka.actor.Actor
import akka.event.Logging.{Debug, Debug3, Error, Error3, Info, Info3, InitializeLogger, LogEvent, LoggerInitialized, Warning, Warning3, simpleName, stackTraceFor}

object AppLogger {
  var println = scala.Predef.println(_: Any)
}

class AppLogger extends Actor {
  val fw = new FileWriter("gs-download.log", true)
  val pw = new PrintWriter(fw)


  override def postStop(): Unit = {
    pw.close()
    super.postStop()
  }

  override def receive: Receive = {
    case InitializeLogger(_) ⇒ sender() ! LoggerInitialized
    case event: LogEvent     ⇒ print(event)
  }

  import java.text.SimpleDateFormat
  import java.util.Date

  private val date = new Date()
  private val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS")

  private final  val ErrorFormat          = "[ERROR] [%s] [%s] [%s] %s%s"
  private final val ErrorFormatWithMarker = "[ERROR] [%s][%s] [%s] [%s] %s%s"

  private final val ErrorFormatWithoutCause           = "[ERROR] [%s] [%s] [%s] %s"
  private final val ErrorWithoutCauseWithMarkerFormat = "[ERROR] [%s][%s] [%s] [%s] %s"

  private final val WarningFormat           = "[WARN] [%s] [%s] [%s] %s"
  private final val WarningWithMarkerFormat = "[WARN] [%s][%s] [%s] [%s] %s"

  private final val InfoFormat           = "[INFO] [%s] [%s] [%s] %s"
  private final val InfoWithMarkerFormat = "[INFO] [%s][%s] [%s] [%s] %s"

  private final val DebugFormat           = "[DEBUG] [%s] [%s] [%s] %s"
  private final val DebugWithMarkerFormat = "[DEBUG] [%s][%s] [%s] [%s] %s"

  def timestamp(event: LogEvent): String = synchronized {
    date.setTime(event.timestamp)
    dateFormat.format(date)
  } // SDF isn't threadsafe

  def print(event: Any): Unit = event match {
    case e: Error   ⇒ error(e)
    case e: Warning ⇒ warning(e)
    case e: Info    ⇒ info(e)
    case e: Debug   ⇒ debug(e)
    case e          ⇒ warning(Warning(simpleName(this), this.getClass, "received unexpected event of class " + e.getClass + ": " + e))
  }

  def error(event: Error): Unit = event match {
    case e: Error3 ⇒ // has marker
      val f = if (event.cause == Error.NoCause) ErrorWithoutCauseWithMarkerFormat else ErrorFormatWithMarker
      println(f.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message,
        stackTraceFor(event.cause)))
    case _ ⇒
      val f = if (event.cause == Error.NoCause) ErrorFormatWithoutCause else ErrorFormat
      println(f.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message,
        stackTraceFor(event.cause)))
  }

  def warning(event: Warning): Unit = event match {
    case e: Warning3 ⇒ // has marker
      println(WarningWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
    case _ ⇒
      println(WarningFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
  }

  def info(event: Info): Unit = event match {
    case e: Info3 ⇒ // has marker
      println(InfoWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
    case _ ⇒
      println(InfoFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
  }

  def debug(event: Debug): Unit = event match {
    case e: Debug3 ⇒ // has marker
      println(DebugWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
    case _ ⇒
      println(DebugFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message))
  }

  def println(s: String) = {
    AppLogger.println(s)
    pw.println(s)
    pw.flush()
  }
}
