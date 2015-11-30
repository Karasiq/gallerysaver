package com.karasiq.gallerysaver.scripting

import com.gargoylesoftware.htmlunit.{CookieManager, WebClient}
import com.karasiq.networkutils.HtmlUnitUtils

trait HtmlUnitGalleryLoader extends GalleryLoader {
  private val cookieManager = {
    // Cookies disabled
    val cm = new CookieManager()
    cm.setCookiesEnabled(false)
    cm
  }

  val webClient: WebClient = HtmlUnitUtils.newWebClient(js = false, cookieManager = cookieManager)
}
