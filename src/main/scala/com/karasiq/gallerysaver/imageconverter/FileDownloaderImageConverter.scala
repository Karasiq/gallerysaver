package com.karasiq.gallerysaver.imageconverter

import java.nio.file.Paths

import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.HttpClientUtils.HttpClientCookie
import com.karasiq.networkutils.downloader.{DownloadedFileReport, FileDownloader, LoadedFile, WrappedFileDownloader}
import com.karasiq.networkutils.http.headers.HttpHeader
import org.apache.commons.io.FilenameUtils

/**
 * Image converter provider
 * @param converter Converter implementation
 * @param extensions File extensions to convert
 * @param suffix Output file suffix
 * @example {{{
 *  // Create converter
 *  val converter = new FileDownloaderImageConverter(new ImageIOConverter("jpg"), Set("png", "bmp"), "_converted")
 *
 *  // Use with FileDownloader
 *  val downloader = new FileDownloader with converter.WithImageConverter
 *  download.download("http://example.com/photo.png", "images") // Will be saved as images/photo_converted.jpg
 * }}}
 */
class FileDownloaderImageConverter(converter: ImageConverter, extensions: Set[String], suffix: String = "_c") {
  private def canConvert(file: String) = {
    extensions.contains(FilenameUtils.getExtension(file))
  }

  /**
   * @param file Source file path
   * @return Destination file path
   */
  private def newName(file: String) = {
    s"${FilenameUtils.removeExtension(file)}$suffix.${converter.newExtension}"
  }

  /**
   * Image converter wrapper trait for [[com.karasiq.networkutils.downloader.FileDownloader FileDownloader]]
   */
  trait WithImageConverter extends WrappedFileDownloader { this: FileDownloader ⇒
    abstract override protected def needLoading(url: String, directory: String, name: String, headers: Seq[HttpHeader], cookies: Traversable[HttpClientCookie]): Boolean = {
      val originalName = FileDownloader.fileNameFor(url, name)
      if (canConvert(originalName)) {
        val path = Paths.get(directory, newName(originalName))
        !path.exists && super.needLoading(url, directory, name, headers, cookies)
      } else super.needLoading(url, directory, name, headers, cookies)
    }

    abstract override protected def onSuccess(report: DownloadedFileReport, file: LoadedFile): Unit = {
      if (canConvert(report.fileName)) {
        val outputFile = newName(report.fileName) // Destination file name
        // log.info(s"Converting downloaded image: ${report.fileName} ⇒ $outputFile")
        converter.convert(ImageSource(report.fileName), ImageDestination(outputFile)) // Create new file
        if (outputFile != report.fileName) asPath(report.fileName).deleteFile() // Remove old file
        super.onSuccess(report.copy(fileName = outputFile), file)
      } else super.onSuccess(report, file)
    }
  }

}
