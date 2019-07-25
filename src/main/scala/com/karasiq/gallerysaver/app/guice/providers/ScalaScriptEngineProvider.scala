package com.karasiq.gallerysaver.app.guice.providers

import java.io.{File, FileWriter}

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import javax.script.ScriptEngine

import scala.language.postfixOps
import scala.tools.nsc.interpreter.JPrintWriter

class ScalaScriptEngineProvider @Inject()(context: GallerySaverContext)
  extends Provider[ScriptEngine] {

  override def get(): ScriptEngine = {
    val se = scala.tools.nsc.interpreter.Scripted(out = new JPrintWriter(new FileWriter(new File("gs-repl.log"), true)))
    se.intp.settings.embeddedDefaults[this.type]
    se.intp.bind("GallerySaverImplicitScriptingContext", "com.karasiq.gallerysaver.scripting.internal.GallerySaverContext", context.copy(scriptEngine = se), List("implicit"))
    se.eval("import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils, Scripts}")
    se.eval("lazy val d = com.karasiq.gallerysaver.scripting.internal.DebugUtils")
    se
  }
}
