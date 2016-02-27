package com.karasiq.gallerysaver.mapdb

import java.nio.file.{Path, Paths}

import com.karasiq.common.Lazy
import com.karasiq.common.Lazy._
import com.karasiq.fileutils.PathUtils._
import com.karasiq.mapdb.serialization.MapDbSerializer
import com.karasiq.mapdb.serialization.MapDbSerializer.Default._
import com.karasiq.mapdb.{MapDbFile, MapDbWrapper}
import com.karasiq.networkutils.HttpClientUtils.HttpClientCookie
import com.karasiq.networkutils.downloader._
import com.karasiq.networkutils.http.headers.HttpHeader

import scala.language.postfixOps

final class FileDownloaderHistory(historyDb: MapDbFile) {
  private val history = Lazy {
    MapDbWrapper(historyDb).createTreeMap[String, FileDownloaderHistoryEntry]("file_downloader") { _
      .keySerializer(MapDbSerializer[String])
      .valueSerializer(MapDbSerializer[FileDownloaderHistoryEntry])
      .nodeSize(32)
      .valuesOutsideNodesEnable()
    }
  }

  @inline
  private def key(p: Path): String = {
    p.toAbsolutePath.toString
  }

  private final implicit class FileDownloaderHistoryReport(report: DownloadedFileReport) {
    def toHistoryEntry = {
      val file = asPath(report.fileName)
      FileDownloaderHistoryEntry(key(file), report.url, file.fileSize, file.lastModified.toInstant)
    }
  }

  trait WithHistory extends WrappedFileDownloader { this: FileDownloader with FileDownloaderActor ⇒
    abstract override protected
    def needLoading(url: String, directory: String, name: String, headers: Seq[HttpHeader], cookies: Traversable[HttpClientCookie]): Boolean = {
      val path = Paths.get(directory, FileDownloader.fileNameFor(url, name))
      val entry = history.get(key(path))
      (!path.exists || path.fileSize == 0 || !entry.exists(e ⇒ e.url == url && e.size == path.fileSize)) && super.needLoading(url, directory, name, headers, cookies)
    }

    abstract override protected
    def onSuccess(report: DownloadedFileReport, file: LoadedFile): Unit = {
      history += (report.fileName → report.toHistoryEntry)
      super.onSuccess(report, file)
    }

    abstract override protected
    def onAlreadyDownloaded(path: Path, url: String, loadedFile: LoadedFile): Boolean = {
      history += (key(path) → FileDownloaderHistoryEntry(key(path), url, path.fileSize, path.lastModified.toInstant))
      super.onAlreadyDownloaded(path, url, loadedFile)
    }
  }
}
