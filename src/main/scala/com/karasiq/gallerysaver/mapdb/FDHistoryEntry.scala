package com.karasiq.gallerysaver.mapdb

import java.time.Instant

final case class FDHistoryEntry(path: String, url: String, size: Long, date: Instant = Instant.now())
