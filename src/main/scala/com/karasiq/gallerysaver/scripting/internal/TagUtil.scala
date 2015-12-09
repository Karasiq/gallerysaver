package com.karasiq.gallerysaver.scripting.internal

import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.util.Try

/**
  * Gallery tag util
  * @param tags Tag set
  * @note Last tags in sequence has priority
  */
final class TagUtil(val tags: Seq[(String, Set[String])]) extends AnyVal {
  def all(name: String): Set[String] = {
    val matchingTags = tags.reverseIterator.collect {
      case (tag, keywords) if keywords.exists(kw ⇒ name.toLowerCase.contains(kw.toLowerCase)) ⇒
        tag
    }
    matchingTags.toSet
  }

  def first(name: String): Option[String] = tags.reverseIterator.collectFirst {
    case (tag, keywords) if keywords.exists(kw ⇒ name.toLowerCase.contains(kw.toLowerCase)) ⇒
      tag
  }
}

object TagUtil {
  def apply(cfg: Config): TagUtil = {
    val tags = Try {
      val tagConfigs = cfg.getConfigList("tags").toIndexedSeq
      tagConfigs.map(c ⇒ c.getString("name") → c.getStringList("keywords").toSet)
    }

    new TagUtil(tags.getOrElse(Nil))
  }
}