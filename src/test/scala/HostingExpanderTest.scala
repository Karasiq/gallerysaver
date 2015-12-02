import com.karasiq.gallerysaver.scripting.LoaderUtils
import org.scalatest.FreeSpec

import scala.collection.JavaConversions._
import scala.concurrent.Await

// Requires network
class HostingExpanderTest extends FreeSpec {
  import TestContext._

  val utils = new LoaderUtils(ec, loader)

  private def test(url: String, result: String): Unit = {
    val future = utils.traverse(url)
    assert(Await.result(future, timeout.duration).resources.exists(_.url == result))
  }

  "Image hosting extractor" - {
    config.getConfigList("image-hostings").foreach { hosting ⇒
      s"should extract ${hosting.getString("name")} image" in {
        test(hosting.getString("url"), hosting.getString("image"))
      }
    }
  }
}
