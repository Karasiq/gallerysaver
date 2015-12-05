package com.karasiq.gallerysaver.scripting.resources

import java.io.OutputStream

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
    s"LoadableGallery[$loader]($url, ${hierarchy.mkString("/", "/", "/")}, ref = ${referrer.getOrElse("<none>")}, cookies = ${if (cookies.isEmpty) "<none>" else cookies.mkString("; ")})"
  }
}

/**
  * Cacheable (immutable) gallery resource
  */
trait CacheableGallery extends LoadableGallery

/**
  * Marks infinite gallery resource (must be limited before loading)
  */
trait InfiniteGallery extends LoadableGallery

/**
  * File resource
  */
trait LoadableFile extends LoadableResource {
  /**
    * File name
    */
  def fileName: Option[String]

  override def toString: String = {
    s"LoadableFile[$loader]($url, ${hierarchy.mkString("/", "/", "/") + fileName.getOrElse("<auto>")}, ref = ${referrer.getOrElse("<none>")}, cookies = ${if (cookies.isEmpty) "<none>" else cookies.mkString("; ")})"
  }
}

/**
  * Generated file resource
  */
trait FileGenerator extends LoadableFile {
  def write(os: OutputStream): Unit

  override def toString: String = {
    s"FileGenerator[$loader]($url, ${hierarchy.mkString("/", "/", "/") + fileName.getOrElse("<auto>")}, ref = ${referrer.getOrElse("<none>")}, cookies = ${if (cookies.isEmpty) "<none>" else cookies.mkString("; ")})"
  }
}