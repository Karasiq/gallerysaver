package com.karasiq.gallerysaver.scripting.internal

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Accept, Cookie, CustomHeader, `User-Agent`}
import akka.http.scaladsl.model.{HttpRequest, MediaRange, MediaTypes}
import com.gargoylesoftware.htmlunit.WebClient

import scala.language.postfixOps

object AkkaHttpUtils {

  case class `X-CSRF-Token`(csrf: String) extends CustomHeader {
    override def name() = "X-CSRF-Token"

    override def value() = csrf

    override def lowercaseName() = "x-csrf-token"

    override def renderInRequests() = true

    override def renderInResponses() = false
  }

  case object `X-Requested-With` extends CustomHeader {
    override def name() = "X-Requested-With"

    override def value() = "XMLHttpRequest"

    override def lowercaseName() = "x-requested-with"

    override def renderInRequests() = true

    override def renderInResponses() = false
  }

  def ajaxHeaders =
    Seq(`X-Requested-With`, Accept(MediaRange(MediaTypes.`application/json`)))

  def mimicCookies(wc: WebClient, domain: String) = {
    import scala.collection.JavaConverters._
    val cookies = wc.getCookieManager.getCookies.asScala
      .filter(_.getDomain.toLowerCase == domain.toLowerCase)
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap

    if (cookies.nonEmpty) Some(Cookie(cookies.toSeq: _*)) else None
  }

  def mimicHeaders(wc: WebClient, domain: String): Seq[akka.http.javadsl.model.HttpHeader] =
    Seq(`User-Agent`(wc.getBrowserVersion.getUserAgent)) ++ mimicCookies(wc, domain)

  def execRequest(request: HttpRequest)(implicit ctx: GallerySaverContext) = {
    import scala.concurrent.duration._, ctx.materializer, materializer.executionContext

    Http()(ctx.actorSystem)
      .singleRequest(request)
      .flatMap(resp => resp.entity.toStrict(10 seconds))
  }

  def execRequestToString(request: HttpRequest)(implicit ctx: GallerySaverContext) = {
    import ctx.materializer.executionContext
    execRequest(request).map(_.data.utf8String)
  }
}
