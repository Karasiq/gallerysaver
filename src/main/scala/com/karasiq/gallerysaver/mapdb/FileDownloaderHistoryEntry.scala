package com.karasiq.gallerysaver.mapdb

import java.time.Instant

final case class FileDownloaderHistoryEntry(fileName: String, url: String, size: Long, date: Instant = Instant.now())
