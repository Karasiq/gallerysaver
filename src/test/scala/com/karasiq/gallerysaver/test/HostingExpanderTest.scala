package com.karasiq.gallerysaver.test

import akka.stream.scaladsl.Sink
import com.karasiq.gallerysaver.scripting.internal.LoaderUtils
import org.scalatest.FreeSpec

import scala.collection.JavaConversions._
import scala.concurrent.Await

// Requires network
class HostingExpanderTest extends FreeSpec {
  import TestContext._

  private def test(url: String, result: String): Unit = {
    val future = LoaderUtils.traverse(url).runWith(Sink.head)
    assert(Await.result(future, timeout.duration).url == result)
  }

  "Image hosting extractor" - {
    config.getConfigList("image-hostings").foreach { hosting ⇒
      s"should extract ${hosting.getString("name")} image" in {
        test(hosting.getString("url"), hosting.getString("image"))
      }
    }
  }
}
