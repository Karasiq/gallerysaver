# About [![Dependencies](https://app.updateimpact.com/badge/692686982173822976/gallerysaver.svg?config=compile)](https://app.updateimpact.com/latest/692686982173822976/gallerysaver)
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
> LoaderUtils.loadAllUrls("http://resource.com/123", "http://resource2.com/123")
> LoaderUtils.loadAllResources(GalleryResource("explicit-loader-id", "http://resource.com/321", hierarchy = Seq("example", "destination", "directory")))
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
  
  override def load(url: String): Future[Iterator[LoadableResource]] = LoaderUtils.asResourcesFuture {
    // Just wrap URL as resources
    ExampleGallery(url)
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

# License
Copyright 2016 Karasiq

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
