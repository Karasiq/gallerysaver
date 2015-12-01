package com.karasiq.gallerysaver.builtin.utils

import com.gargoylesoftware.htmlunit.html.HtmlAnchor
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url.URLParser

object ImageAnchor {
  private val defaultImageExtensions = Set("jpg", "jpeg", "bmp", "png", "gif")

  def unapply(anchor: HtmlAnchor): Option[HtmlAnchor] = anchor match {
    case a: HtmlAnchor if defaultImageExtensions.contains(URLParser(a.fullHref).file.extension.toLowerCase) ⇒
      Some(a)

    case _ ⇒
      None
  }
}
