package com.karasiq.gallerysaver.app.guice.providers

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.imageconverter.FileDownloaderImageConverter
import com.karasiq.gallerysaver.mapdb.{FDHistoryStore, FileDownloaderHistory}
import com.karasiq.networkutils.downloader.{FileDownloaderActor, FileDownloaderTraits, HttpClientFileDownloader}

class FileDownloaderProvider @Inject()(store: FDHistoryStore, actorSystem: ActorSystem) extends Provider[ActorRef] {
  private[this] def props() = {
    val history = new FileDownloaderHistory(store)
    val converter = FileDownloaderImageConverter.fromConfig(actorSystem.settings.config.getConfig("gallery-saver.image-converter"))
    Props(new HttpClientFileDownloader with FileDownloaderActor with history.WithHistory with converter.WithImageConverter with FileDownloaderTraits.CheckSize with FileDownloaderTraits.CheckModified)
  }

  override def get(): ActorRef = {
    actorSystem.actorOf(this.props(), "defaultFileDownloader")
  }
}
