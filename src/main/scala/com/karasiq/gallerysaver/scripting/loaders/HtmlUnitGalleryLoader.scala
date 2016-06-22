package com.karasiq.gallerysaver.scripting.loaders

import java.net.URL

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.util.Cookie
import com.gargoylesoftware.htmlunit.{CookieManager, Page, WebClient}
import com.karasiq.common.ThreadLocalFactory
import com.karasiq.gallerysaver.scripting.internal.{GallerySaverContext, LoaderUtils}
import com.karasiq.gallerysaver.scripting.resources.LoadableResource
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

// TODO: Dispatch, JSoup, JSON wrappers
object HtmlUnitGalleryLoader {
  private val factory: ThreadLocalFactory[WebClient] = ThreadLocalFactory.softRef(newWebClient(js = false, cookieManager = newCookieManager()), _.close())

  /**
    * Creates new cookie manager
    * @param enabled Cookies enabled
    * @return Cookie manager
    */
  def newCookieManager(enabled: Boolean = true): CookieManager = {
    val cm = new CookieManager()
    cm.setCookiesEnabled(enabled)
    cm
  }

  /**
    * Creates web client with default settings
    * @return HtmlUnit web client
    */
  def createWebClient(): WebClient = {
    factory()
  }
}

/**
  * Basic HtmlUnit loader
  */
trait HtmlUnitGalleryLoader extends GalleryLoader {
  protected def extractCookies(resource: LoadableResource): Map[String, String] = {
    resource.cookies ++ extractCookiesForUrl(resource.url)
  }

  // Used for external resources, to avoid cookie leak
  protected final def extractCookiesForUrl(url: String): Map[String, String] = {
    Try(this.extractCookies(new URL(url).getHost)) match {
      case Success(cookies) ⇒
        cookies

      case Failure(_) ⇒
        // Invalid URL
        Map.empty
    }
  }

  protected final def extractCookies(domain: String): Map[String, String] = {
    webClient.getCookieManager.getCookies
      .filter(_.getDomain.toLowerCase == domain.toLowerCase)
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap
  }

  protected def withResource[T <: LoadableResource](resource: LoadableResource)(f: PartialFunction[Page, Source[T, akka.NotUsed]])(implicit ctx: GallerySaverContext): Source[T, akka.NotUsed] = {
    implicit val ec = ctx.executionContext
    val future = LoaderUtils.future {
      val cookies = {
        val cm = new CookieManager
        this.compileCookies(resource).foreach(cm.addCookie)
        cm
      }

      concurrent.blocking {
        val wc: WebClient = this.webClient
        wc.withCookies(cookies)(wc.withGetPage(resource.url)(f.orElse[Page, Source[T, NotUsed]] { case _ ⇒ Source.empty[T] }))
      }
    }
    Source.fromFuture(future).flatMapConcat(identity)
  }

  protected def compileCookies(resource: LoadableResource): Iterator[Cookie] = {
    Try {
      val url = new URL(resource.url)
      resource.cookies.toIterator.map { case (k, v) ⇒
        new Cookie(s".${url.getHost}", k, v, "/", 10000000, url.getProtocol.equalsIgnoreCase("https"))
      }
    } getOrElse Iterator.empty
  }

  def webClient: WebClient = HtmlUnitGalleryLoader.createWebClient()
}
