package com.karasiq.gallerysaver.mapdb

import java.time.Instant

import scala.collection.mutable

trait FDHistoryStore extends mutable.AbstractMap[String, FDHistoryEntry]

final class H2FDHistoryStore(sql: AppSQLContext) extends FDHistoryStore {

  import sql._
  import context.{lift => liftQ, _}

  private[this] object Model extends PredefEncoders {

    final case class DBHistoryEntry(path: String, fileName: String, url: String, size: Long, date: Instant) {
      def toEntry = FDHistoryEntry(fileName, url, size, date)
    }

    object DBHistoryEntry {
      def fromPathAndEntry(path: String, entry: FDHistoryEntry) =
        DBHistoryEntry(path, entry.fileName, entry.url, entry.size, entry.date)
    }

    implicit val historySchemaMeta = schemaMeta[DBHistoryEntry]("fdHistory")
  }

  import Model._

  override def get(key: String): Option[FDHistoryEntry] = {
    val q = quote(query[DBHistoryEntry].filter(_.path == liftQ(key)).map(_.toEntry))
    context.run(q).headOption
  }

  override def iterator: Iterator[(String, FDHistoryEntry)] = {
    val q = quote(query[DBHistoryEntry].map(e => (e.path, e.toEntry)))
    context.run(q).iterator
  }

  override def +=(kv: (String, FDHistoryEntry)): H2FDHistoryStore.this.type = {
    val q = quote {
      val path = liftQ(kv._1)
      query[DBHistoryEntry].insert(_.path -> liftQ(kv._1), _.fileName -> liftQ(kv._2.fileName), _.url -> liftQ(kv._2.url), _.size -> liftQ(kv._2.size), _.date -> liftQ(kv._2.date))
    }
    context.run(q)
    this
  }

  override def -=(key: String): H2FDHistoryStore.this.type = {
    val q = quote(query[DBHistoryEntry].filter(_.path == liftQ(key)).delete)
    context.run(q)
    this
  }
}
