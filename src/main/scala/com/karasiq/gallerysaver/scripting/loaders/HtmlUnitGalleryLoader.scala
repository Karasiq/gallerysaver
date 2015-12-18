package com.karasiq.gallerysaver.scripting.loaders

import java.net.URL

import com.gargoylesoftware.htmlunit.util.Cookie
import com.gargoylesoftware.htmlunit.{CookieManager, Page, WebClient}
import com.karasiq.common.ThreadLocalFactory
import com.karasiq.gallerysaver.scripting.resources.LoadableResource
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

// TODO: Dispatch, JSoup, JSON wrappers
object HtmlUnitGalleryLoader {
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

  private val factory: ThreadLocalFactory[WebClient] = ThreadLocalFactory.softRef(newWebClient(js = false, cookieManager = newCookieManager()), _.close())

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
  protected final def extractCookies(domain: String): Map[String, String] = {
    webClient.getCookieManager.getCookies
      .filter(_.getDomain.toLowerCase == domain.toLowerCase)
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap
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

  protected def extractCookies(resource: LoadableResource): Map[String, String] = {
    resource.cookies ++ extractCookiesForUrl(resource.url)
  }

  protected def compileCookies(resource: LoadableResource): Iterator[Cookie] = {
    Try {
      val host = new URL(resource.url).getHost
      resource.cookies.toIterator.map { case (k, v) ⇒
        new Cookie(host, k, v, "/", 10000000, false)
      }
    } getOrElse Iterator.empty
  }

  protected def withResource[T <: LoadableResource](resource: LoadableResource)(f: PartialFunction[Page, Iterator[T]]): Iterator[T] = {
    val cookies = {
      val cm = new CookieManager
      this.compileCookies(resource).foreach(cm.addCookie)
      cm
    }

    val wc: WebClient = this.webClient
    wc.withCookies(cookies) {
      wc.withGetPage(resource.url)(f.orElse[Page, Iterator[T]] { case _ ⇒ Iterator.empty })
    }
  }

  def webClient: WebClient = HtmlUnitGalleryLoader.createWebClient()
}
