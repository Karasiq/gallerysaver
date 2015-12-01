package com.karasiq.gallerysaver.app.guice.providers

import akka.actor.ActorSystem
import com.google.inject.{Inject, Provider}
import com.typesafe.config.Config

class ActorSystemProvider @Inject() (config: Config) extends Provider[ActorSystem] {
  override def get(): ActorSystem = {
    ActorSystem("gallery-saver", config)
  }
}
