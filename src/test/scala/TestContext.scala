import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.google.inject.Guice
import com.google.inject.name.Names
import com.karasiq.gallerysaver.app.guice.GallerySaverModule
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._

import scala.concurrent.duration._
import scala.language.postfixOps

object TestContext {
  final val injector = Guice.createInjector(new GallerySaverModule)

  val loader = injector.instance[ActorRef](Names.named("gallerySaverDispatcher"))

  implicit val timeout: Timeout = Timeout(5 minutes)

  implicit val ec = injector.instance[ActorSystem].dispatcher

  val config = injector.instance[Config].getConfig("gallery-saver.test")
}
