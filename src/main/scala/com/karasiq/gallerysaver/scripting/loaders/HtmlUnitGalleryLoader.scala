package com.karasiq.gallerysaver.scripting.loaders

import java.net.URL

import com.gargoylesoftware.htmlunit.util.Cookie
import com.gargoylesoftware.htmlunit.{CookieManager, Page, WebClient}
import com.karasiq.common.ThreadLocalFactory
import com.karasiq.gallerysaver.scripting.resources.LoadableResource
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.collection.JavaConversions._

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

trait HtmlUnitGalleryLoader extends GalleryLoader {
  protected final def extractCookies(domain: String): Map[String, String] = {
    webClient.getCookieManager.getCookies
      .filter(_.getDomain.toLowerCase == domain.toLowerCase)
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap
  }

  protected final def extractCookies(resource: LoadableResource): Map[String, String] = {
    resource.cookies ++ extractCookies(new URL(resource.url).getHost)
  }

  protected def compileCookies(resource: LoadableResource): Iterator[Cookie] = {
    val host = new URL(resource.url).getHost
    resource.cookies.toIterator.map { case (k, v) ⇒
      new Cookie(host, k, v, "/", 10000000, false)
    }
  }

  protected def withResource[T <: LoadableResource](resource: LoadableResource)(f: PartialFunction[Page, Iterator[T]]): Iterator[T] = {
    val cookies = {
      val cm = new CookieManager
      this.compileCookies(resource).foreach(cm.addCookie)
      cm
    }

    webClient.withCookies(cookies) {
      webClient.withGetPage(resource.url)(f.orElse[Page, Iterator[T]] { case _ ⇒ Iterator.empty })
    }
  }

  def webClient: WebClient = HtmlUnitGalleryLoader.createWebClient()
}
