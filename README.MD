# About
GallerySaver is customizable internet galleries downloader.

# Use REPL
```
sbt run
```

## In REPL console
* Print available loader IDs:
```scala
> Loaders.idSet
```

* Direct loading:
```scala
> LoaderUtils.loadAllFiles("http://resource.com/123")
> LoaderUtils.loadAllFiles(GalleryResource("explicit-loader-id", "http://resource.com/321", hierarchy = Seq("example", "destination", "directory")))
```

* Execute script:
```scala
> Scripts.evalFile("ExampleScript.scala")
```

# Fast batch loading
* Write in `scripts/AutoExec.scala`:
```scala
object Shortcuts {
  def exampleAction(): Unit = {
    // Do something
    ...
    
    // Or execute script
    Scripts.evalFile("scripts/Example.scala")
  }
}
```

* Call it from REPL:
```scala
> Shortcuts.exampleAction()
```

# Creating custom loader
* Create file `Example.scala` (put it in `loaders`, or `loaders-private` directory for automatic loading, or execute it manually)

* Create resource definition:
```scala
import com.karasiq.gallerysaver.scripting.resources._

case class ExampleGallery(url: String, hierarchy: Seq[String] = Seq("example", "unsorted"), referrer: Option[String] = None, cookies: Map[String, String] = Map.empty, loader: String = "example") extends LoadableGallery
```

* Write code of loader:
```scala
import com.karasiq.gallerysaver.scripting.loaders._

class ExampleLoader extends GalleryLoader { // Or use HtmlUnitGalleryLoader for predefined HtmlUnit interfaces
  override def id: String = "example"
  
  override def canLoadUrl(url: String): Boolean = {
    url.contains("http://example.com")
  }
  
  override def load(url: String): Future[Iterator[LoadableResource]] = {
    Future.successfully(Iterator(ExampleGallery(url))) // Just wrap URL as resource
  }
  
  override def load(resource: LoadableResource): Future[Iterator[LoadableResource]] = LoaderUtils.future {
    val files: Iterator[String] = ??? // Parse file URLs
    files.map(fileUrl => FileResource(this.id, fileUrl, Some(resource.url), Map.empty, resource.hierarchy :+ "example subdirectory")) // Wrap URLs as file resources
  }
}
```

* Register loader:
```scala
Loaders.register[ExampleLoader]
```

* Load script in active REPL:
```scala
Scripts.evalFile("Example.scala")
```