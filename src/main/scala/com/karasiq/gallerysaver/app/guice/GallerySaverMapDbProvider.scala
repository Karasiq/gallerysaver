package com.karasiq.gallerysaver.app.guice

import com.google.inject.{Inject, Provider}
import com.karasiq.fileutils.PathUtils._
import com.karasiq.mapdb.{MapDbFile, MapDbFileProducer}
import com.typesafe.config.Config
import org.mapdb.DBMaker.Maker

private object GallerySaverMapDbProducer extends MapDbFileProducer {
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

class GallerySaverMapDbProvider @Inject()(config: Config) extends Provider[MapDbFile] {
  override def get(): MapDbFile = {
    GallerySaverMapDbProducer(asPath(config.getString("gallery-saver.db")))
  }
}
