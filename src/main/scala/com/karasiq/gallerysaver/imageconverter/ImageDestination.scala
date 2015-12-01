package com.karasiq.gallerysaver.imageconverter

import java.io.{Closeable, OutputStream}

import com.karasiq.common.Lazy
import com.karasiq.fileutils.PathUtils.{PathProvider, _}

/**
  * Abstract image destination
  */
abstract class ImageDestination extends Closeable {
  def outputStream: OutputStream
}

object ImageDestination {
  def apply(os: OutputStream, closeStream: Boolean = true): ImageDestination = new ImageDestination {
    override def outputStream: OutputStream = os

    override def close(): Unit = if (closeStream) os.close() else ()
  }

  def apply[T](path: T)(implicit pp: PathProvider[T]): ImageDestination = new ImageDestination {

    private val outputStream_ = Lazy(pp(path).outputStream())

    override def outputStream: OutputStream = outputStream_()

    override def close(): Unit = outputStream_.ifDefined(_.close())
  }
}