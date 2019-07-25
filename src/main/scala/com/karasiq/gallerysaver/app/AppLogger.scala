package com.karasiq.gallerysaver.app

import java.io.{FileWriter, PrintWriter}

import akka.actor.Actor
import akka.event.Logging.{Debug, Debug3, Error, Error3, Info, Info3, InitializeLogger, LogEvent, LoggerInitialized, Warning, Warning3, simpleName, stackTraceFor}

object AppLogger {
  @volatile
  private[app] var printF = (f: String, _: Any) => scala.Predef.println(f)

  def log(a: Any): Unit = printF(a.toString, a)
}

class AppLogger extends Actor {
  private[this] val fileWriter = new PrintWriter(new FileWriter("gs-download.log", true))

  override def postStop(): Unit = {
    fileWriter.close()
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

  def timestamp(event: LogEvent): String = {
    date.setTime(event.timestamp)
    dateFormat.format(date)
  }

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
      printEvent(f.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message,
        stackTraceFor(event.cause)), event.message)
    case _ ⇒
      val f = if (event.cause == Error.NoCause) ErrorFormatWithoutCause else ErrorFormat
      printEvent(f.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message,
        stackTraceFor(event.cause)), event.message)
  }

  def warning(event: Warning): Unit = event match {
    case e: Warning3 ⇒ // has marker
      printEvent(WarningWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
    case _ ⇒
      printEvent(WarningFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
  }

  def info(event: Info): Unit = event match {
    case e: Info3 ⇒ // has marker
      printEvent(InfoWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
    case _ ⇒
      printEvent(InfoFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
  }

  def debug(event: Debug): Unit = event match {
    case e: Debug3 ⇒ // has marker
      printEvent(DebugWithMarkerFormat.format(
        e.marker.name,
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
    case _ ⇒
      printEvent(DebugFormat.format(
        timestamp(event),
        event.thread.getName,
        event.logSource,
        event.message), event.message)
  }

  private[this] def printEvent(s: String, msg: Any): Unit = {
    AppLogger.printF(s, msg)
    fileWriter.println(s)
    fileWriter.flush()
  }
}
