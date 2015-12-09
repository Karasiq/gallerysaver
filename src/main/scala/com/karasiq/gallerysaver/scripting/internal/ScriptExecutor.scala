package com.karasiq.gallerysaver.scripting.internal

import java.io.Reader
import javax.script.ScriptEngine

import scala.io.{BufferedSource, Source}
import scala.util.control.Exception

/**
  * Script execution provider
  * @param scriptEngine Scripting engine
  */
final class ScriptExecutor(scriptEngine: ⇒ ScriptEngine) {
  /**
    * Executes provided script
    * @param script Script text
    * @return Evaluation result
    */
  def eval(script: String): AnyRef = {
    scriptEngine.eval(script)
  }

  /**
    * Reads and executes script from reader
    * @param reader Reader
    * @return Evaluation result
    */
  def evalReader(reader: Reader): AnyRef = {
    scriptEngine.eval(reader)
  }

  /**
    * Reads and executes script from source
    * @param source Source
    * @return Evaluation result
    */
  def evalSource(source: BufferedSource): AnyRef = {
    evalReader(source.bufferedReader())
  }

  /**
    * Reads and executes script from file
    * @param file File path
    * @return Evaluation result
    */
  def evalFile(file: String): AnyRef = {
    val source = Source.fromFile(file, "UTF-8")
    Exception.allCatch.andFinally(source.close()) {
      evalSource(source)
    }
  }
}
