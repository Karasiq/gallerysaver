package com.karasiq.gallerysaver.scripting

import java.nio.file.Paths

import com.karasiq.networkutils.downloader.{FileDownloader, FileToDownload}
import org.apache.http.impl.cookie.BasicClientCookie

/**
  * Generic internet resource
  */
sealed trait LoadableResource {
  /**
    * Loader ID
    */
  def loader: String

  /**
    * Resource URL
    */
  def url: String

  /**
    * Referrer
    */
  def referrer: Option[String]

  /**
    * Cookies
    */
  def cookies: Map[String, String]

  /**
    * Path hierarchy
    */
  def hierarchy: Seq[String]
}

/**
  * Gallery resource
  */
trait LoadableGallery extends LoadableResource {
  override def toString: String = {
    s"LoadableGallery[$loader]($url, ${hierarchy.mkString("/", "/", "/")}, ref = ${referrer.getOrElse("<none>")}, cookies = ${if (cookies.isEmpty) "<none>" else cookies.mkString(", ")})"
  }
}

/**
  * Cacheable (immutable) gallery resource
  */
trait CacheableGallery extends LoadableGallery with Serializable

/**
  * File resource
  */
trait LoadableFile extends LoadableResource {
  /**
    * File name
    */
  def fileName: Option[String]

  def asFileToDownload: FileToDownload = {
    val path = Paths.get(hierarchy.head, hierarchy.tail:_*)
    val cookies = this.cookies.map { case (k, v) ⇒
      new BasicClientCookie(k, v)
    }
    FileToDownload(url, path.toString, fileName.getOrElse(""), referrer.map(FileDownloader.referer(_)).toList, cookies, sendReport = true)
  }

  override def toString: String = {
    s"LoadableFile[$loader]($url, ${hierarchy.mkString("/", "/", "/")}, ${fileName.getOrElse("<auto>")})"
  }
}
