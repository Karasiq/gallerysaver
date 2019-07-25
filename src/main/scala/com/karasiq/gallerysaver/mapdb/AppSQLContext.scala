package com.karasiq.gallerysaver.mapdb

import java.time.Instant
import java.util.Date

import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{H2JdbcContext, SnakeCase}

class AppSQLContext(config: Config) extends AutoCloseable {
  val dbConfig: Config = createH2Config()
  val context = new H2JdbcContext[SnakeCase](dbConfig)

  import scala.collection.JavaConverters._

  private[this] def createH2Config(): Config = {
    val path = config.getString("path")
    val script = config.getString("init-script")
    ConfigFactory.parseMap(Map(
      "dataSourceClassName" → "org.h2.jdbcx.JdbcDataSource",
      "dataSource.url" → s"jdbc:h2:file:$path;INIT=RUNSCRIPT FROM '$script'",
      "dataSource.user" → "sa",
      "dataSource.password" → "sa"
    ).asJava)
  }

  trait PredefEncoders {
    import context._
    implicit val encodeInstant = MappedEncoding[Instant, Date](Date.from)
    implicit val decodeInstant = MappedEncoding[Date, Instant](_.toInstant)
  }

  override def close(): Unit = {
    context.close()
  }
}
