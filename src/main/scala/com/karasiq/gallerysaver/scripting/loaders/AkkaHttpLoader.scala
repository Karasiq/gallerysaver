package com.karasiq.gallerysaver.scripting.loaders

import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{Cookie, Referer}
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

class AkkaHttpLoader(implicit ctx: GallerySaverContext) {
  protected final val http = Http(ctx.actorSystem)
  private[this] implicit val materializer: Materializer = ctx.materializer

  protected def withHttpResource[T <: LoadableResource](resource: LoadableResource): Source[HttpResponse, akka.NotUsed] = {
    val requestHeaders = List(
      Some(resource.cookies).filter(_.nonEmpty).map(cs ⇒ Cookie(cs.toList: _*)),
      resource.referrer.map(Referer(_))
    ).flatten

    val request = HttpRequest(uri = resource.url, headers = requestHeaders)
    Source.fromFuture(http.singleRequest(request))
  }
}
