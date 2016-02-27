package com.karasiq.gallerysaver.scripting.loaders

import akka.http.scaladsl._
import akka.http.scaladsl.model.headers.{Cookie, Referer}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.karasiq.gallerysaver.scripting.internal.GallerySaverContext
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

import scala.concurrent.Future

abstract class AkkaHttpLoader(implicit ctx: GallerySaverContext) {
  protected final implicit val actorMaterializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(ctx.actorSystem))(ctx.actorSystem)

  protected final val http = Http(ctx.actorSystem)

  protected def withHttpResource[T <: LoadableResource](resource: LoadableResource)(f: PartialFunction[HttpResponse, Future[Iterator[T]]]): Future[Iterator[T]] = {
    val requestHeaders = List(Some(Cookie(resource.cookies.toSeq: _*)), resource.referrer.map(Referer(_))).flatten
    val future = http.singleRequest(HttpRequest(uri = resource.url, headers = requestHeaders))
    future.flatMap(response ⇒ f.applyOrElse(response, _ ⇒ Future.successful(Iterator.empty)))(ctx.executionContext)
  }
}
