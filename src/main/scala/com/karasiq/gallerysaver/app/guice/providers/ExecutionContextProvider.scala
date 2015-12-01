package com.karasiq.gallerysaver.app.guice.providers

import java.util.concurrent.Executors

import com.google.inject.{Inject, Provider}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class ExecutionContextProvider @Inject() (config: Config) extends Provider[ExecutionContext] {
  override def get(): ExecutionContext = {
    val parallelism = config.getInt("gallery-saver.execution-context.parallelism")
    require(parallelism > 0, "Invalid parallelism value")
    ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(parallelism))
  }
}
