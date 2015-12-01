package com.karasiq.gallerysaver.app.guice.providers

import akka.actor.ActorSystem
import com.google.inject.{Inject, Provider}

import scala.concurrent.ExecutionContext

class ExecutionContextProvider @Inject() (actorSystem: ActorSystem) extends Provider[ExecutionContext] {
  override def get(): ExecutionContext = {
    actorSystem.dispatchers.lookup("gallery-saver.dispatcher")
  }
}
