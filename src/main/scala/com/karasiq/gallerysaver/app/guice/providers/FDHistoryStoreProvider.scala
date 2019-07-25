package com.karasiq.gallerysaver.app.guice.providers

import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.mapdb.{AppSQLContext, FDHistoryStore, H2FDHistoryStore}

class FDHistoryStoreProvider @Inject()(sql: AppSQLContext) extends Provider[FDHistoryStore] {
  override def get(): FDHistoryStore = {
    new H2FDHistoryStore(sql)
  }
}
