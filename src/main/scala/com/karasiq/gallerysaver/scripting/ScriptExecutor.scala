package com.karasiq.gallerysaver.scripting

import java.io.Reader
import javax.script.ScriptEngine

import scala.io.{BufferedSource, Source}
import scala.util.control.Exception

final class ScriptExecutor(scriptEngine: ⇒ ScriptEngine) {
  def eval(script: String): AnyRef = {
    scriptEngine.eval(script)
  }

  def evalReader(reader: Reader): AnyRef = {
    scriptEngine.eval(reader)
  }

  def evalSource(source: BufferedSource): AnyRef = {
    evalReader(source.bufferedReader())
  }

  def evalFile(file: String): AnyRef = {
    val source = Source.fromFile(file, "UTF-8")
    Exception.allCatch.andFinally(source.close()) {
      evalSource(source)
    }
  }
}
