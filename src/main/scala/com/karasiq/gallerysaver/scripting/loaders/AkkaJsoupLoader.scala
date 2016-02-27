package com.karasiq.gallerysaver.scripting.loaders

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.resources.LoadableResource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AkkaJSoupLoader(implicit ctx: GallerySaverContext) extends AkkaHttpLoader {
  protected def withHtmlPage[T <: LoadableResource](resource: LoadableResource): Source[Document, akka.NotUsed] = {
    withHttpResource(resource).collect {
      case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
        entity.dataBytes
          .fold(ByteString.empty)(_ ++ _)
          .map(bytes ⇒ Jsoup.parse(bytes.utf8String))
    }.flatMapConcat(identity)
  }
}
