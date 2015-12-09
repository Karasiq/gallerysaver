package com.karasiq.gallerysaver.dispatcher

import com.karasiq.gallerysaver.scripting.resources.LoadableResource

case class LoadedResources(resources: Seq[LoadableResource])

object LoadedResources {
  val empty: LoadedResources = LoadedResources(Nil)
}
