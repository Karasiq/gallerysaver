package com.karasiq.gallerysaver.builtin.utils

import com.gargoylesoftware.htmlunit.html.HtmlAnchor

object ImageAnchor {
  def unapply(anchor: HtmlAnchor): Option[HtmlAnchor] = {
    ImageExpander.extensionFilter().lift(anchor)
  }
}
