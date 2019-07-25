package com.karasiq.gallerysaver.mapdb

import java.time.Instant

import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{H2JdbcContext, SnakeCase}

class AppSQLContext(config: Config) {
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

  import context._
  implicit val encodeInstant = MappedEncoding[Instant, Long](_.toEpochMilli)
  implicit val decodeInstant = MappedEncoding[Long, Instant](Instant.ofEpochMilli)
}
