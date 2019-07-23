import com.gargoylesoftware.htmlunit.html.HtmlElement
import com.karasiq.gallerysaver.scripting.internal.Scripts
import com.karasiq.networkutils.url._

import scala.util.Try
import scala.util.matching.Regex

object debug {
  val HtmlUnitUtils = com.karasiq.networkutils.HtmlUnitUtils
  import HtmlUnitUtils._

  lazy val webClient = newWebClient(js = false)

  def parseUrl(url: String) =
    URLParser(url)

  def runScript(s: String) =
    Scripts.evalFile(s"scripts/$s.scala")

  def linksList(ss: Seq[String]): String = {
    val result = new StringBuilder
    for (s <- ss) {
      if (result.nonEmpty) result ++= ",\n"
      result ++= "  " += '"' ++= s += '"'
    }
    "Vector(\n" + result.result() + "\n)"
  }

  final case class Page(url: String) {
    lazy val htmlPage = webClient.htmlPageOption(url)

    def links: Seq[String] = for {
      p <- htmlPage.toVector.distinct
      a <- p.anchors
      href <- Try(a.fullHref).toOption
    } yield href

    def localLinks: Seq[String] =
      links.filter(l => URLParser(l).host == URLParser(htmlPage.get.getBaseURL).host)

    def elements(s: String): Seq[HtmlElement] = for {
      p <- htmlPage.toVector
      el <- p.byXPath[HtmlElement](s)
    } yield el

    def regex(s: String): Seq[Regex.Match] = for {
      p <- htmlPage.toVector
      pXml = p.asXml()
      m <- s.r.findAllMatchIn(pXml)
    } yield m
  }
}
