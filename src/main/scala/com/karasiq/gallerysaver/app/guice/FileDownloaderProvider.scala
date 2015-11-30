package com.karasiq.gallerysaver.app.guice

import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{Inject, Provider}
import com.karasiq.networkutils.downloader.FileDownloaderActor

class FileDownloaderProvider @Inject()(actorSystem: ActorSystem) extends Provider[ActorRef] {
  private val downloader = actorSystem.actorOf(Props(FileDownloaderActor()), "defaultFileDownloader")

  override def get(): ActorRef = {
    downloader
  }
}
