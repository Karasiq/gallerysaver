package com.karasiq.gallerysaver.builtin

import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage}

object ImagePreview {
  def unapply(img: HtmlImage): Option[HtmlAnchor] = {
    img.getParentNode match {
      case a: HtmlAnchor ⇒
        Some(a)

      case _ ⇒
        None
    }
  }
}
