package com.karasiq.gallerysaver.builtin.utils

import java.net.URL
import java.nio.file.{Files, Paths}

import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage}
import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url.URLParser

import scala.collection.{GenSeq, GenTraversableOnce}
import scala.util.Try

object ImageExpander {
  import scala.language.implicitConversions

  type FilterFunction[T <: AnyRef] = PartialFunction[AnyRef, T]
  type ExpanderFunction[T <: AnyRef] = PartialFunction[AnyRef, GenTraversableOnce[T]]

  implicit def filterFunctionAsExpanderFunction[T <: AnyRef](f: FilterFunction[T]): ExpanderFunction[T] = f.asExpanderFunction

  implicit class FilterFunctionConversion[T <: AnyRef](f: FilterFunction[T]) {
    def asExpanderFunction: ExpanderFunction[T] = {
      f.andThen(t ⇒ Some(t))
    }
  }

  implicit class ExpanderFunctionConversion[T <: AnyRef](f: ExpanderFunction[T]) {
    def asFilterFunction: FilterFunction[T] = {
      f.andThen(_.toIterable.head)
    }
  }

  val defaultImageExtensions = Set("jpg", "jpeg", "bmp", "png", "gif")

  def source: FilterFunction[AnyRef] = {
    case s: String ⇒ s
    case a: HtmlAnchor ⇒ a
    case img: HtmlImage ⇒ img
  }

  def previews: FilterFunction[HtmlAnchor] = {
    case ImagePreview(anchor) ⇒
      anchor
  }

  private def isValidURL(url: ⇒ String): Boolean = {
    Try(new URL(url)).isSuccess
  }

  def extensionFilter(ext: Set[String] = defaultImageExtensions): FilterFunction[HtmlAnchor] = {
    case a: HtmlAnchor if isValidURL(a.fullHref) && ext.contains(URLParser(a.fullHref).file.extension.toLowerCase) ⇒
      a
  }

  def sequencedFilter[T <: AnyRef](f: ExpanderFunction[T]*): ExpanderFunction[T] = {
    f.reduce((f1, f2) ⇒ f1.andThen(_.toIterable.flatMap(e ⇒ f2.lift(e).getOrElse(Nil))))
  }

  def sequenced(f: ExpanderFunction[_ <: AnyRef]*): ExpanderFunction[AnyRef] = {
    f.reduce((f1, f2) ⇒ f1.andThen(_.toIterable.flatMap(e ⇒ f2.orElse(source.asExpanderFunction).lift(e).getOrElse(Nil))))
  }

  /**
    * Unifies the HTML content to URL
    */
  def downloadableUrl: FilterFunction[String] = {
    case img: HtmlImage if isValidURL(img.fullSrc) ⇒
      img.fullSrc

    case a: HtmlAnchor if isValidURL(a.fullHref) ⇒
      a.fullHref

    case a: Page ⇒
      a.getUrl.toString

    case url: String if isValidURL(url) ⇒
      url
  }

  implicit class PageMixedContentOps(val buffer: GenTraversableOnce[AnyRef]) {
    def images: GenTraversableOnce[HtmlImage] = buffer.toIterable.collect {
      case img: HtmlImage ⇒ img
    }

    def anchors: GenTraversableOnce[HtmlAnchor] = buffer.toIterable.collect {
      case a: HtmlAnchor ⇒ a
    }

    def links: GenSeq[String] = buffer.toSeq.collect(downloadableUrl).distinct

    def fileNames = links.map(url ⇒ URLParser(url).file.name)

    def filterLinksByExt(extensions: Set[String] = ImageExpander.defaultImageExtensions) = {
      links.filter(url ⇒ extensions.contains(URLParser(url).file.extension))
    }

    def expandFilter[T <: AnyRef](f: ExpanderFunction[T]): GenTraversableOnce[T] = buffer.toIterator.flatMap(f.orElse { case _ ⇒ Nil })
    def expand[T <: AnyRef](f: ExpanderFunction[T]): GenTraversableOnce[AnyRef] = expandFilter(f.orElse(source.asExpanderFunction))
  }

  implicit class PageImagesOps(buffer: GenTraversableOnce[HtmlImage]) extends PageMixedContentOps(buffer) {
    def filterImagesBySize(width: Int = 0, height: Int = 0) = buffer.toIterable.filter(img ⇒ img.getHeight > height && img.getWidth > width)

    def expandedPreviews = expandFilter(ImageExpander.previews)

    def expandedPreviewsOrImages = expand(ImageExpander.previews)

    def saveAll(directory: String, names: GenTraversableOnce[String]): Unit = {
      Files.createDirectories(asPath(directory))
      buffer.toIterable.zip(names.toIterable).par foreach { // Parallel
        case (image, fileName) ⇒
          image.saveAs(Paths.get(directory, fileName).toFile)
      }
    }

    def saveAll(directory: String, nameFunc: HtmlImage ⇒ String): Unit =
      saveAll(directory, buffer.toIterable.map(nameFunc))

    def saveAll(directory: String): Unit =
      saveAll(directory, fileNames)
  }
}
