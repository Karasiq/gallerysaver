import akka.pattern.ask
import com.karasiq.gallerysaver.dispatcher.LoadedResources
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.language.postfixOps

class PreviewLoaderTest extends FlatSpec with Matchers {
  import TestContext._

  "Preview loader" should "load test URL" in {
    val url = config.getString("preview-url")
    val future = (loader ? url).mapTo[LoadedResources].flatMap(r ⇒ loader ? r.resources.head)
    val result = Await.result(future.mapTo[LoadedResources], timeout.duration)
    result.resources.head.url shouldBe config.getString("preview-result")
  }
}
