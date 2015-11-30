package com.karasiq.gallerysaver.scripting

import java.net.URL

import com.gargoylesoftware.htmlunit.{CookieManager, WebClient}
import com.karasiq.networkutils.HtmlUnitUtils

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

  protected val webClient: WebClient = HtmlUnitUtils.newWebClient(js = false, cookieManager = newCookieManager(false))
}
