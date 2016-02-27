package com.karasiq.gallerysaver.dispatcher

import akka.stream.scaladsl.Source
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

case class LoadedResources(resources: Source[LoadableResource, akka.NotUsed]) extends AnyVal

object LoadedResources {
  val empty: LoadedResources = LoadedResources(Source.empty)
}
