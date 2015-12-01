package com.karasiq.gallerysaver.app.guice.providers

import java.nio.file.{Files, Paths}

import com.google.inject.Provider
import com.typesafe.config.{Config, ConfigFactory}

class ExternalConfigProvider extends Provider[Config] {
  override def get(): Config = {
    val defaultConfig = ConfigFactory.load()
    val fileName = defaultConfig.getString("gallery-saver.external-config")

    Paths.get(fileName) match {
      case file if Files.isRegularFile(file) && Files.isReadable(file) ⇒
        ConfigFactory.parseFile(file.toFile)
          .withFallback(defaultConfig)
          .resolve()

      case _ ⇒
        defaultConfig
    }
  }
}
