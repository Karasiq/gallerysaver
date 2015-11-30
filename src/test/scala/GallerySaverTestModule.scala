import java.io.File

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.karasiq.mapdb.MapDbFile
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule
import org.mapdb.DBMaker

class GallerySaverTestModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[Config].toInstance(ConfigFactory.parseFile(new File("test.conf")))
    bind[MapDbFile].toInstance(MapDbFile(DBMaker.heapDB().transactionDisable().make()))
    bind[ActorSystem].toInstance(ActorSystem("gallery-saver-test"))
  }
}
