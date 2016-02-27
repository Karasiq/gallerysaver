package com.karasiq.gallerysaver.builtin.utils

import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.karasiq.gallerysaver.builtin.utils.ImageExpander._
import com.karasiq.networkutils.HtmlUnitUtils._

/**
  * Plain paged gallery helper
  */
trait PagedSiteImageExtractor {
  /**
    * Returns images source
    * @param htmlPage Initial HTML page
    * @return Source of (url, generated name)
    */
  def getImagesSource(htmlPage: HtmlPage): Source[(String, String), akka.NotUsed] = {
    Source.fromIterator(() ⇒ this.pagesIterator(htmlPage).zipWithIndex)
      .flatMapConcat {
        case (page, pageNumber) ⇒
          getPagePreviews(page).zip(Source.fromIterator(() ⇒ Iterator.from(0)))
            .map { case (image, imageNumber) ⇒ (image, this.imageSaveName(pageNumber, imageNumber, image, page)) }
      }
  }

  private def pagesIterator(htmlPage: HtmlPage): Iterator[HtmlPage] = {
    PaginationUtils.htmlPageIterator(htmlPage, htmlPage ⇒ nextPageOption(htmlPage))
  }

  protected def imageSaveName(pageNumber: Int, imageIndex: Int, url: String, page: HtmlPage) =
    s"${pageNumber}_$imageIndex"

  private def getPagePreviews(page: Page): Source[String, akka.NotUsed] = page match {
    case htmlPage: HtmlPage ⇒
      val images = htmlPage.images
      val anchors = htmlPage.anchors
      val expanded = images.expandFilter(imageExpander).concat(anchors.expandFilter(anchorExpander))
      expanded.expandFilter(downloadableUrl)

    case _ ⇒
      Source.empty
  }

  protected def imageExpander: ExpanderFunction[AnyRef] =
    sequencedFilter(previews, extensionFilter())

  protected def anchorExpander: ExpanderFunction[AnyRef] =
    extensionFilter()

  protected def nextPageOption(page: HtmlPage): Option[HtmlPage]
}
