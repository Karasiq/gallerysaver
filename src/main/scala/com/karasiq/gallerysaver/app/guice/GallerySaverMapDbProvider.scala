package com.karasiq.gallerysaver.app.guice

import com.google.inject.{Inject, Provider}
import com.karasiq.fileutils.PathUtils._
import com.karasiq.mapdb.{MapDbFile, MapDbSingleFileProducer}
import com.typesafe.config.Config
import org.mapdb.DBMaker.Maker

class GallerySaverMapDbProvider @Inject()(config: Config) extends Provider[MapDbFile] {
  private val producer = new MapDbSingleFileProducer(asPath(config.getString("gallery-saver.db"))) {
    override protected def setSettings(dbMaker: Maker): Maker = {
      dbMaker
        .compressionEnable()
        .executorEnable()
        .cacheSoftRefEnable()
        .transactionDisable()
        .asyncWriteEnable()
        .asyncWriteFlushDelay(4000)
    }
  }

  override def get(): MapDbFile = {
    producer()
  }
}
