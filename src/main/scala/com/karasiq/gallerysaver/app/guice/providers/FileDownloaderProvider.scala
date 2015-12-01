package com.karasiq.gallerysaver.app.guice.providers

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.gallerysaver.imageconverter.{FileDownloaderImageConverter, ImageIOConverter}
import com.karasiq.gallerysaver.mapdb.FileDownloaderHistory
import com.karasiq.mapdb.MapDbFile
import com.karasiq.networkutils.downloader.{FileDownloaderActor, FileDownloaderTraits, HttpClientFileDownloader}

class FileDownloaderProvider @Inject()(mapDbFile: MapDbFile, actorSystem: ActorSystem) extends Provider[ActorRef] {
  private def props() = {
    val history = new FileDownloaderHistory(mapDbFile)
    val converter = new FileDownloaderImageConverter(new ImageIOConverter("jpg"), Set("png", "bmp"))
    Props(new HttpClientFileDownloader with FileDownloaderActor with history.WithHistory with converter.WithImageConverter with FileDownloaderTraits.CheckSize with FileDownloaderTraits.CheckModified)
  }

  override def get(): ActorRef = {
    actorSystem.actorOf(this.props(), "defaultFileDownloader")
  }
}
