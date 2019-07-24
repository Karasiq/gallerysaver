package com.karasiq.gallerysaver.app.guice.providers

import java.io.{File, FileWriter}

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import javax.script.{ScriptEngine, ScriptEngineManager}

import scala.language.postfixOps
import scala.tools.nsc.interpreter.JPrintWriter

class ScalaScriptEngineProvider @Inject()(context: GallerySaverContext)
  extends Provider[ScriptEngine] {

  override def get(): ScriptEngine = {
    new ScriptEngineManager().getEngineByName("scala") match {
      case si: scala.tools.nsc.interpreter.IMain ⇒
        val si1 = new scala.tools.nsc.interpreter.IMain(si.factory, si.settings, new JPrintWriter(new FileWriter(new File("gs-repl.log"), true)))

        si1.settings.embeddedDefaults[this.type]
        si1.settings.usejavacp.value = true

        si1.bind("GallerySaverImplicitScriptingContext", "com.karasiq.gallerysaver.scripting.internal.GallerySaverContext", context.copy(scriptEngine = si1), List("implicit"))
        si1.eval("import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils, Scripts}")
        si1.eval("lazy val d = com.karasiq.gallerysaver.scripting.internal.DebugUtils")
        si1

      case _ ⇒
        throw new IllegalArgumentException("Invalid scala interpreter")
    }
  }
}
