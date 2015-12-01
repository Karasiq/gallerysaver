package com.karasiq.gallerysaver.imageconverter

import java.awt.image._
import java.awt.{Image, Toolkit}
import java.io.{Closeable, InputStream}
import javax.imageio.ImageIO

import com.karasiq.fileutils.PathUtils.PathProvider

/**
  * Abstract image source
  */
abstract class ImageSource extends Closeable {
  def image: BufferedImage
}

object ImageSource {
  private sealed abstract class ImageStreamSource extends ImageSource {
    def inputStream: InputStream

    override final lazy val image: BufferedImage = ImageIO.read(inputStream)
  }

  def apply(is: InputStream, closeStream: Boolean = true): ImageSource = new ImageStreamSource {
    override def inputStream: InputStream = is

    override def close(): Unit = if (closeStream) is.close()
  }

  def apply[T](path: T)(implicit pp: PathProvider[T]): ImageSource = new ImageSource {
    // JPG colors fix
    def fixImage(img: Image): BufferedImage = {
      val RGB_MASKS = Array(0xFF0000, 0xFF00, 0xFF)
      val RGB_OPAQUE: ColorModel = new DirectColorModel(32, RGB_MASKS(0), RGB_MASKS(1), RGB_MASKS(2))

      val pg = new PixelGrabber(img, 0, 0, -1, -1, true)
      pg.grabPixels()

      (pg.getWidth, pg.getHeight, pg.getPixels) match {
        case (width, height, pixels: Array[Int]) ⇒
          val buffer = new DataBufferInt(pixels, width * height)
          val raster = Raster.createPackedRaster(buffer, width, height, width, RGB_MASKS, null)
          new BufferedImage(RGB_OPAQUE, raster, false, null)

        case _ ⇒
          throw new IllegalArgumentException("Invalid image")
      }
    }

    override def image: BufferedImage = {
      val img = Toolkit.getDefaultToolkit.createImage(pp(path).toString)
      fixImage(img)
    }

    override def close(): Unit = ()
  }

  def apply(img: BufferedImage): ImageSource = new ImageSource {
    override def image: BufferedImage = img

    override def close(): Unit = ()
  }
}
