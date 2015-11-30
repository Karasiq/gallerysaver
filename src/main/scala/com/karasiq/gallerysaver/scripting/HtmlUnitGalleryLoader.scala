package com.karasiq.gallerysaver.scripting

import com.gargoylesoftware.htmlunit.{CookieManager, WebClient}
import com.karasiq.networkutils.HtmlUnitUtils

import scala.collection.JavaConversions._

trait HtmlUnitGalleryLoader extends GalleryLoader {
  protected def newCookieManager(enabled: Boolean): CookieManager = {
    val cm = new CookieManager()
    cm.setCookiesEnabled(enabled)
    cm
  }

  protected def extractCookies(): Map[String, String] = {
    webClient.getCookieManager.getCookies
      .map(cookie ⇒ cookie.getName → cookie.getValue).toMap
  }

  protected val webClient: WebClient = HtmlUnitUtils.newWebClient(js = false, cookieManager = newCookieManager(false))
}
