package com.karasiq.gallerysaver.imageconverter

import javax.imageio.ImageIO

import org.apache.commons.io.IOUtils

import scala.util.control.Exception

/**
  * Java ImageIO image converter
  * @param formatName Destination format name
  */
final class ImageIOConverter(formatName: String) extends ImageConverter {
  override def newExtension: String = formatName

  override def convert(input: ImageSource, output: ImageDestination): Unit = {
    def closeAfter[T]: Exception.Catch[T] = Exception.allCatch.andFinally {
      IOUtils.closeQuietly(input)
      IOUtils.closeQuietly(output)
    }

    closeAfter(ImageIO.write(input.image, formatName, output.outputStream))
  }
}
