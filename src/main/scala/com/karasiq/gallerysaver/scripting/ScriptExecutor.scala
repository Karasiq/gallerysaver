package com.karasiq.gallerysaver.scripting

import java.io.{FileInputStream, InputStreamReader, Reader}
import javax.script.ScriptEngine

import org.apache.commons.io.IOUtils

import scala.util.control.Exception

final class ScriptExecutor(scriptEngine: ⇒ ScriptEngine) {
  def eval(script: String): AnyRef = {
    scriptEngine.eval(script)
  }

  def evalReader(reader: Reader): AnyRef = {
    scriptEngine.eval(reader)
  }

  def evalFile(file: String): AnyRef = {
    val reader = new InputStreamReader(new FileInputStream(file))
    Exception.allCatch.andFinally(IOUtils.closeQuietly(reader)) {
      evalReader(reader)
    }
  }
}
