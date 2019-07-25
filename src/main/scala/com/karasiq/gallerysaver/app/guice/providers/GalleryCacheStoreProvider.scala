package com.karasiq.gallerysaver.app.guice.providers

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.mapdb.{AppSQLContext, GalleryCacheStore, H2GalleryCacheStore}

class GalleryCacheStoreProvider @Inject()(sql: AppSQLContext) extends Provider[GalleryCacheStore] {
  override def get(): GalleryCacheStore = {
    new H2GalleryCacheStore(sql)
  }
}
