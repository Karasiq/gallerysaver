package com.karasiq.gallerysaver.app.guice.providers

import javax.script.{ScriptEngine, ScriptEngineManager}

import scala.language.postfixOps

import com.google.inject.{Inject, Provider}

import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext

class ScalaScriptEngineProvider @Inject()(context: GallerySaverContext)
  extends Provider[ScriptEngine] {

  override def get(): ScriptEngine = {
    new ScriptEngineManager().getEngineByName("scala") match {
      case scalaInterpreter: scala.tools.nsc.interpreter.IMain ⇒
        scalaInterpreter.settings.embeddedDefaults[this.type]
        scalaInterpreter.settings.usejavacp.value = true
        scalaInterpreter.bind("GallerySaverImplicitScriptingContext", "com.karasiq.gallerysaver.scripting.internal.GallerySaverContext", context.copy(scriptEngine = scalaInterpreter), List("implicit"))
        scalaInterpreter.eval("import com.karasiq.gallerysaver.scripting.internal.{Loaders, LoaderUtils, Scripts}")
        scalaInterpreter

      case _ ⇒
        throw new IllegalArgumentException("Invalid scala interpreter")
    }
  }
}
