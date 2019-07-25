package com.karasiq.gallerysaver.mapdb

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import com.karasiq.gallerysaver.scripting.resources.LoadableResource

import scala.util.control.NonFatal

trait GalleryCacheStore extends collection.mutable.AbstractMap[String, Seq[LoadableResource]]

class H2GalleryCacheStore(sql: AppSQLContext) extends GalleryCacheStore {
  import sql._
  import context.{lift => liftQ, _}

  private[this] object Model {
    type Resources = Seq[LoadableResource]
    case class DBGalleryCache(url: String, resources: Resources)

    implicit val historySchemaMeta = schemaMeta[DBGalleryCache]("galleryCache")

    implicit val encodeResources = MappedEncoding[Resources, Array[Byte]] { rs =>
      val bs = new ByteArrayOutputStream()
      val obs = new ObjectOutputStream(bs)
      obs.writeObject(rs)
      obs.close()
      bs.toByteArray
    }

    implicit val decodeResources = MappedEncoding[Array[Byte], Resources] { bs =>
      val ois = new ObjectInputStream(new ByteArrayInputStream(bs))
      ois.readObject().asInstanceOf[Resources]
    }
  }

  import Model._

  override def +=(kv: (String, Seq[LoadableResource])): H2GalleryCacheStore.this.type = {
    val upd = quote(query[DBGalleryCache].filter(_.url == liftQ(kv._1)).update(_.resources -> liftQ(kv._2)))
    if (context.run(upd) == 0) {
      val ins = quote(query[DBGalleryCache].insert(_.url -> liftQ(kv._1), _.resources -> liftQ(kv._2)))
      context.run(ins)
    }
    this
  }

  override def -=(key: String): H2GalleryCacheStore.this.type = {
    val q = quote(query[DBGalleryCache].filter(_.url == liftQ(key)).delete)
    context.run(q)
    this
  }

  override def get(key: String): Option[Seq[LoadableResource]] = {
    val q = quote(query[DBGalleryCache].filter(_.url == liftQ(key)).map(_.resources))
    context.run(q).headOption
  }

  override def iterator: Iterator[(String, Seq[LoadableResource])] = {
    val q = quote(query[DBGalleryCache].map(e => (e.url, e.resources)))
    context.run(q).iterator
  }
}
