package com.karasiq.gallerysaver.scripting

import java.net.URL

import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.util.Cookie
import com.gargoylesoftware.htmlunit.{CookieManager, WebClient}
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url._

import scala.collection.JavaConversions._

trait HtmlUnitGalleryLoader extends GalleryLoader {
  protected final def newCookieManager(enabled: Boolean): CookieManager = {
    val cm = new CookieManager()
    cm.setCookiesEnabled(enabled)
    cm
  }

  protected final def extractCookies(domain: String): Map[String, String] = {
    webClient.getCookieManager.getCookies
      .filter(_.getDomain.toLowerCase == domain.toLowerCase)
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap
  }

  protected final def extractCookies(resource: LoadableResource): Map[String, String] = {
    resource.cookies ++ extractCookies(new URL(resource.url).getHost)
  }

  protected def withResource[T](resource: LoadableResource)(f: HtmlPage ⇒ T): T = {
    val cookies = {
      val host = new URL(resource.url).getHost
      val cm = new CookieManager
      resource.cookies.foreach { case (k, v) ⇒
        cm.addCookie(new Cookie(host, k, v, "/", 10000000, false))
      }
      cm
    }
    webClient.withCookies(cookies) {
      webClient.withGetHtmlPage(resource.url)(f)
    }
  }

  protected val webClient: WebClient = newWebClient(js = false, cookieManager = newCookieManager(false))
}
