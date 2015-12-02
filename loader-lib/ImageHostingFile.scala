import java.util.concurrent.TimeUnit

import com.karasiq.gallerysaver.builtin.ImageHostingResource
import com.karasiq.gallerysaver.builtin.utils.ImageHostingExtractor._
import com.karasiq.gallerysaver.dispatcher.LoadedResources

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}


/**
  * Image hosting extraction helper object
  */
object ImageHostingFile {
  /**
    * Script engine provided execution context
    */
  private implicit def ec: ExecutionContext = LoaderPool

  /**
    * User-defined image hosting extractors. Has priority over predefined ones.
    * @see [[com.karasiq.gallerysaver.builtin.utils.ImageHostingExtractor ImageHostingExtractor]]
    * @see [[com.karasiq.networkutils.HtmlUnitUtils HtmlUnitUtils]]
    * @example {{{
    *   expandImageHosting("example.org/image", _.firstByXPath[HtmlImage]("/html/body/center/img"))
    * }}}
    */
  private def userDefinedExtractors: PartialFunction[AnyRef, Iterator[AnyRef]] = {
    Seq(
      // Nothing here now
    ).fold(PartialFunction.empty)(_ orElse _)
  }

  /**
    * Fetches resources from image hosting
    * @param url Image hosting URL
    * @return Resources
    */
  def resources(url: String): Future[LoadedResources] = {
    LoaderUtils.get(ImageHostingResource(url))
  }

  /**
    * Fetches file URLs from image hosting
    * @param url Image hosting URL
    * @return Image URLs
    */
  def files(url: String): Future[Seq[String]] = {
    resources(url).map(_.resources.map(_.url))
  }

  def apply(): PartialFunction[AnyRef, Seq[String]] = {
    case v if unifyToUrl.isDefinedAt(v) && userDefinedExtractors.orElse(predefinedExtractors).isDefinedAt(unifyToUrl(v)) ⇒
      Await.result(files(unifyToUrl(v)), FiniteDuration(5, TimeUnit.MINUTES))
  }

  def unapplySeq(v: AnyRef): Option[Seq[String]] = {
    this.apply().lift(v)
  }
}
