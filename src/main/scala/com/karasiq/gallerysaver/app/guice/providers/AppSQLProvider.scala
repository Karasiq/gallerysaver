package com.karasiq.gallerysaver.app.guice.providers

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.mapdb.AppSQLContext
import com.typesafe.config.Config

class AppSQLProvider @Inject()(config: Config) extends Provider[AppSQLContext] {
  override def get(): AppSQLContext = {
    new AppSQLContext(config.getConfig("gallery-saver.db"))
  }
}
