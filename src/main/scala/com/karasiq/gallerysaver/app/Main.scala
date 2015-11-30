package com.karasiq.gallerysaver.app

import javax.script.ScriptEngine

import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.gallerysaver.app.guice.GallerySaverModule
import net.codingwell.scalaguice.InjectorExtensions._

object Main extends App {
  val injector = Guice.createInjector(new GallerySaverModule)
  val engine = injector.instance[ScriptEngine](Names.named("scala"))
  println(engine.eval(
    """
      |Loaders.forId("anus")
    """.stripMargin))
}
