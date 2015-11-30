package com.karasiq.gallerysaver.imageconverter

import java.awt.image._
import java.awt.{Image, Toolkit}
import java.io.{Closeable, InputStream, OutputStream}
import javax.imageio.ImageIO

import com.karasiq.common.Lazy
import com.karasiq.fileutils.PathUtils.{PathProvider, _}
import org.apache.commons.io.IOUtils

import scala.util.control.Exception

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

abstract class ImageConverter {
  def newExtension: String
  def convert(input: ImageSource, output: ImageDestination): Unit
}

final class ImageIOConverter(formatName: String) extends ImageConverter {
  override def newExtension: String = formatName

  override def convert(input: ImageSource, output: ImageDestination): Unit = {
    val closeAfter = Exception.allCatch.andFinally {
      IOUtils.closeQuietly(input)
      IOUtils.closeQuietly(output)
    }

    closeAfter { ImageIO.write(input.image, formatName, output.outputStream) }
  }
}
