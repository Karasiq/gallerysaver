package com.karasiq.gallerysaver.mapdb

import java.nio.file.{Path, Paths}

import com.karasiq.fileutils.PathUtils._
import com.karasiq.networkutils.HttpClientUtils.HttpClientCookie
import com.karasiq.networkutils.downloader._
import com.karasiq.networkutils.http.headers.HttpHeader

import scala.language.postfixOps

final class FileDownloaderHistory(store: FDHistoryStore) {
  trait WithHistory extends WrappedFileDownloader { this: FileDownloader with FileDownloaderActor ⇒
    abstract override protected
    def needLoading(url: String, directory: String, name: String, headers: Seq[HttpHeader], cookies: Traversable[HttpClientCookie]): Boolean = {
      val path = Paths.get(directory, FileDownloader.fileNameFor(url, name))
      val entry = store.get(key(path))
      (!path.exists || path.fileSize == 0 || !entry.exists(e ⇒ e.url == url && e.size == path.fileSize)) && super.needLoading(url, directory, name, headers, cookies)
    }

    abstract override protected
    def onSuccess(report: DownloadedFileReport, file: LoadedFile): Unit = {
      store += (report.fileName → report.toHistoryEntry)
      super.onSuccess(report, file)
    }

    abstract override protected
    def onAlreadyDownloaded(path: Path, url: String, loadedFile: LoadedFile): Boolean = {
      store += (key(path) → FDHistoryEntry(key(path), url, path.fileSize, path.lastModified.toInstant))
      super.onAlreadyDownloaded(path, url, loadedFile)
    }
  }

  private[this] implicit class FileDownloaderHistoryReport(report: DownloadedFileReport) {
    def toHistoryEntry: FDHistoryEntry = {
      val file = asPath(report.fileName)
      FDHistoryEntry(key(file), report.url, file.fileSize, file.lastModified.toInstant)
    }
  }

  @inline
  private[this] def key(p: Path): String = p.toAbsolutePath.toString
}
