import akka.stream.scaladsl.Source
import com.karasiq.gallerysaver.builtin.ImageHostingResource
import com.karasiq.gallerysaver.builtin.utils.ImageHostingExtractor._
import com.karasiq.gallerysaver.scripting.internal.LoaderUtils
import com.karasiq.gallerysaver.scripting.resources.LoadableResource

/**
  * Image hosting extraction helper object
  */
object ImageHostingFile extends LoaderUtils.ContextBindings {
  def unapply(v: AnyRef): Option[Source[String, akka.NotUsed]] = {
    this.apply().lift(v)
  }

  def apply(): PartialFunction[AnyRef, Source[String, akka.NotUsed]] = {
    case v if unifyToUrl.isDefinedAt(v) && userDefinedExtractors.orElse(predefinedExtractors).isDefinedAt(unifyToUrl(v)) ⇒
      resources(unifyToUrl(v)).map(_.url)
  }

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
  def resources(url: String): Source[LoadableResource, akka.NotUsed] = {
    LoaderUtils.asSource(ImageHostingResource(url))
  }
}
