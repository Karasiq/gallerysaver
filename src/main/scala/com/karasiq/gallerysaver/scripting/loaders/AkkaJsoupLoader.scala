package com.karasiq.gallerysaver.scripting.loaders

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.resources.LoadableResource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.concurrent.Future

class AkkaJSoupLoader(implicit ctx: GallerySaverContext) extends AkkaHttpLoader {
  protected def withHtmlPage[T <: LoadableResource](resource: LoadableResource)(f: PartialFunction[Document, Iterator[T]]): Future[Iterator[T]] = {
    withHttpResource(resource) {
      case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
        entity.dataBytes
          .fold(ByteString.empty)(_ ++ _)
          .map(bytes ⇒ f.applyOrElse(Jsoup.parse(bytes.utf8String), _ ⇒ Iterator.empty))
          .runWith(Sink.head)
    }
  }
}
