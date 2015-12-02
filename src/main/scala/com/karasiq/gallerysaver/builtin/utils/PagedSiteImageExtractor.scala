package com.karasiq.gallerysaver.builtin.utils

import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.networkutils.HtmlUnitUtils._

/**
  * Plain paged gallery helper
  */
trait PagedSiteImageExtractor {
  protected def nextPageOption(page: HtmlPage): Option[HtmlPage]

  private def pagesIterator(htmlPage: HtmlPage): Iterator[HtmlPage] = {
    PaginationUtils.htmlPageIterator(htmlPage, htmlPage ⇒ nextPageOption(htmlPage))
  }

  protected def imageExpander: ExpanderFunction[AnyRef] =
    sequencedFilter(previews, extensionFilter())

  protected def anchorExpander: ExpanderFunction[AnyRef] =
    extensionFilter()

  protected def imageSaveName(pageNumber: Int, imageIndex: Int, url: String, page: HtmlPage) =
    s"${pageNumber}_$imageIndex"

  private def getPagePreviews(page: Page): Iterator[String] = page match {
    case htmlPage: HtmlPage ⇒
      val images = htmlPage.images
      val anchors = htmlPage.anchors
      val expanded = images.expandFilter(imageExpander).toIterator ++ anchors.expandFilter(anchorExpander)
      expanded.expandFilter(downloadableUrl).toIterator

    case _ ⇒
      Iterator.empty
  }

  /**
    * Returns iterator of images
    * @param page Initial HTML page
    * @return Iterator of (url, generated name)
    */
  def getImagesIterator(page: HtmlPage): Iterator[(String, String)] = {
    for {
      (page, pageNumber) <- this.pagesIterator(page).zipWithIndex
      (image, imageNumber) <- this.getPagePreviews(page).zipWithIndex
    } yield (image, this.imageSaveName(pageNumber, imageNumber, image, page))
  }
}
