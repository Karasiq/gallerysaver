package com.karasiq.gallerysaver.test

import akka.stream.scaladsl.Sink
import com.karasiq.gallerysaver.scripting.internal.LoaderUtils
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.language.postfixOps

class PreviewLoaderTest extends FlatSpec with Matchers {
  import TestContext._

  "Preview loader" should "load test URL" in {
    val url = config.getString("preview-url")
    val future = LoaderUtils.traverse(url).runWith(Sink.head)
    val result = Await.result(future, timeout.duration)
    result.url shouldBe config.getString("preview-result")
  }
}
