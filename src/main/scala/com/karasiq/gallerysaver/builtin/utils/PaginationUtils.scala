package com.karasiq.gallerysaver.builtin.utils

import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.{Page, WebClient}
import com.karasiq.common.Lazy
import com.karasiq.networkutils.url.URLParser

import scala.collection.AbstractIterator

private sealed abstract class PageIterator[P <: Page](startPage: P) extends AbstractIterator[P] {
  private var currentPage: Lazy[Option[P]] = Lazy(Some(startPage))

  def nextPage(page: P): Option[P]

  override def hasNext: Boolean = currentPage().nonEmpty

  override def next(): P = {
    val next: P = currentPage().getOrElse(throw new NoSuchElementException("Last page reached"))
    currentPage = Lazy(nextPage(next))
    next
  }
}

object PaginationUtils {
  def pagedUrl(url: String, page: Int, parameterName: String = "page"): String = URLParser(url)
    .appendQuery(parameterName → String.valueOf(page))
    .toString()

  def urlIterator(startUrl: String, pagesRange: Range, parameterName: String = "page"): Iterator[String] = {
    pagesRange.toIterator.map(pn ⇒ pagedUrl(startUrl, pn, parameterName))
  }

  def htmlPageIterator(startUrl: String, pagesRange: Range, parameterName: String = "page")(implicit wc: WebClient): Iterator[HtmlPage] = {
    def pageByUrl(url: String): HtmlPage = wc.getPage[HtmlPage](url)
    urlIterator(startUrl, pagesRange, parameterName).map(pageByUrl)
  }

  def htmlPageIterator(startPage: HtmlPage, nextPageFunction: HtmlPage ⇒ Option[HtmlPage]): Iterator[HtmlPage] = {
    new PageIterator[HtmlPage](startPage) {
      override def nextPage(page: HtmlPage): Option[HtmlPage] = {
        nextPageFunction(page)
      }
    }
  }
}
