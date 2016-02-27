package com.karasiq.gallerysaver.scripting.loaders

import akka.http.scaladsl._
import akka.http.scaladsl.model.headers.{Cookie, Referer}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

class AkkaHttpLoader(implicit ctx: GallerySaverContext) {
  protected final val http = Http(ctx.actorSystem)
  private implicit val actorMaterializer: ActorMaterializer = ctx.actorMaterializer

  protected def withHttpResource[T <: LoadableResource](resource: LoadableResource): Source[HttpResponse, akka.NotUsed] = {
    val requestHeaders = List(Some(Cookie(resource.cookies.toSeq: _*)), resource.referrer.map(Referer(_))).flatten
    Source.fromFuture(http.singleRequest(HttpRequest(uri = resource.url, headers = requestHeaders)))
  }
}
