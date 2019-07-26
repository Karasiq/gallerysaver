package com.karasiq.gallerysaver.mapdb

import scala.collection.mutable

trait FDHistoryStore extends mutable.AbstractMap[String, FDHistoryEntry]

final class H2FDHistoryStore(sql: AppSQLContext) extends FDHistoryStore {

  import sql._
  import context.{lift => liftQ, _}

  private[this] object Model extends PredefEncoders {
    implicit val historySchemaMeta = schemaMeta[FDHistoryEntry]("fdHistory")
  }

  import Model._

  override def get(key: String): Option[FDHistoryEntry] = {
    val q = quote(query[FDHistoryEntry].filter(_.path == liftQ(key)))
    context.run(q).headOption
  }

  override def iterator: Iterator[(String, FDHistoryEntry)] = {
    val q = quote(query[FDHistoryEntry].map(e => (e.path, e)))
    context.run(q).iterator
  }

  override def +=(kv: (String, FDHistoryEntry)): H2FDHistoryStore.this.type = {
    val upd = quote {
      val path = liftQ(kv._1)
      query[FDHistoryEntry].filter(_.path == liftQ(kv._1)).update(_.url -> liftQ(kv._2.url), _.size -> liftQ(kv._2.size), _.date -> liftQ(kv._2.date))
    }

    if (context.run(upd) == 0) {
      val ins = quote {
        val path = liftQ(kv._1)
        query[FDHistoryEntry].insert(_.path -> liftQ(kv._1), _.url -> liftQ(kv._2.url), _.size -> liftQ(kv._2.size), _.date -> liftQ(kv._2.date))
      }
      context.run(ins)
    }

    this
  }

  override def -=(key: String): H2FDHistoryStore.this.type = {
    val q = quote(query[FDHistoryEntry].filter(_.path == liftQ(key)).delete)
    context.run(q)
    this
  }
}
