package com.karasiq.gallerysaver.builtin.utils

import java.net.URL
import java.nio.file.{Files, Paths}

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html.{HtmlAnchor, HtmlImage}
import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.HtmlUnitUtils._
import com.karasiq.networkutils.url.URLParser

import scala.collection.{GenIterable, GenSeq, GenTraversableOnce}
import scala.util.Try

object ImageExpander {
  import scala.language.implicitConversions

  type FilterFunction[T <: AnyRef] = PartialFunction[AnyRef, T]
  type ExpanderFunction[T <: AnyRef] = PartialFunction[AnyRef, Source[T, akka.NotUsed]]
  val defaultImageExtensions = Set("jpg", "jpeg", "bmp", "png", "gif")

  implicit def filterFunctionAsExpanderFunction[T <: AnyRef](f: FilterFunction[T]): ExpanderFunction[T] = {
    f.asExpanderFunction
  }

  def previews: FilterFunction[HtmlAnchor] = {
    case ImagePreview(anchor) ⇒
      anchor
  }

  def extensionFilter(ext: Set[String] = defaultImageExtensions): FilterFunction[HtmlAnchor] = {
    case a: HtmlAnchor if isValidURL(a.fullHref) && ext.contains(URLParser(a.fullHref).file.extension.toLowerCase) ⇒
      a
  }

  private def isValidURL(url: ⇒ String): Boolean = {
    Try(new URL(url)).isSuccess
  }

  def sequencedFilter[T <: AnyRef](f: ExpanderFunction[T]*): ExpanderFunction[T] = {
    f.reduce((f1, f2) ⇒ f1.andThen(_.flatMapConcat(e ⇒ f2.lift(e).getOrElse(Source.empty))))
  }

  def sequenced(f: ExpanderFunction[_ <: AnyRef]*): ExpanderFunction[AnyRef] = {
    f.reduce((f1, f2) ⇒ f1.andThen(_.flatMapConcat(e ⇒ f2.orElse(source.asExpanderFunction).lift(e).getOrElse(Source.empty))))
  }

  def source: FilterFunction[AnyRef] = {
    case s: String ⇒ s
    case a: HtmlAnchor ⇒ a
    case img: HtmlImage ⇒ img
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

  implicit class FilterFunctionConversion[T <: AnyRef](val f: FilterFunction[T]) extends AnyVal {
    def asExpanderFunction: ExpanderFunction[T] = {
      f.andThen(Source.single)
    }
  }

  implicit class PageMixedContentSourceOps(val buffer: Source[AnyRef, akka.NotUsed]) extends AnyVal {
    def images: Source[HtmlImage, akka.NotUsed] = buffer.collect {
      case img: HtmlImage ⇒ img
    }

    def anchors: Source[HtmlAnchor, akka.NotUsed] = buffer.collect {
      case a: HtmlAnchor ⇒ a
    }

    def fileNames: Source[String, NotUsed] = {
      links.map(url ⇒ URLParser(url).file.name)
    }

    def filterLinksByExt(extensions: Set[String] = ImageExpander.defaultImageExtensions): Source[String, NotUsed] = {
      links.filter(url ⇒ extensions.contains(URLParser(url).file.extension))
    }

    def links: Source[String, akka.NotUsed] = {
      buffer.collect(downloadableUrl)
    }

    def expand[T <: AnyRef](f: ExpanderFunction[T]): Source[AnyRef, akka.NotUsed] = {
      expandFilter(f.orElse(source.asExpanderFunction))
    }

    def expandFilter[T <: AnyRef](f: ExpanderFunction[T]): Source[T, akka.NotUsed] = {
      buffer
        .flatMapConcat(f.orElse { case _ ⇒ Source.empty })
    }
  }

  implicit class PageMixedContentOps(val buffer: GenTraversableOnce[AnyRef]) extends AnyVal {
    def images: GenTraversableOnce[HtmlImage] = buffer.toIterable.collect {
      case img: HtmlImage ⇒ img
    }

    def anchors: GenTraversableOnce[HtmlAnchor] = buffer.toIterable.collect {
      case a: HtmlAnchor ⇒ a
    }

    def fileNames: GenSeq[String] = {
      links.map(url ⇒ URLParser(url).file.name)
    }

    def links: GenSeq[String] = {
      buffer.toSeq.collect(downloadableUrl).distinct
    }

    def filterLinksByExt(extensions: Set[String] = ImageExpander.defaultImageExtensions): GenSeq[String] = {
      links.filter(url ⇒ extensions.contains(URLParser(url).file.extension))
    }

    def expand[T <: AnyRef](f: ExpanderFunction[T]): Source[AnyRef, akka.NotUsed] = {
      expandFilter(f.orElse(source.asExpanderFunction))
    }

    def expandFilter[T <: AnyRef](f: ExpanderFunction[T]): Source[T, akka.NotUsed] = {
      Source.fromIterator(() ⇒ buffer.toIterator).expandFilter(f)
    }
  }

  implicit class PageImagesOps(val buffer: GenTraversableOnce[HtmlImage]) extends AnyVal {
    def filterImagesBySize(width: Int = 0, height: Int = 0): GenIterable[HtmlImage] = {
      buffer.toIterable.filter(img ⇒ img.getHeight > height && img.getWidth > width)
    }

    def expandedPreviews: Source[HtmlAnchor, NotUsed] = {
      buffer.expandFilter(ImageExpander.previews)
    }

    def expandedPreviewsOrImages: Source[AnyRef, NotUsed] = {
      buffer.expand(ImageExpander.previews)
    }

    def saveAll(directory: String, nameFunc: HtmlImage ⇒ String): Unit = {
      saveAll(directory, buffer.toIterable.map(nameFunc))
    }

    def saveAll(directory: String, names: GenTraversableOnce[String]): Unit = {
      Files.createDirectories(asPath(directory))
      buffer.toIterable.zip(names.toIterable).par foreach { // Parallel
        case (image, fileName) ⇒
          image.saveAs(Paths.get(directory, fileName).toFile)
      }
    }

    def saveAll(directory: String): Unit = {
      saveAll(directory, buffer.fileNames)
    }
  }
}
