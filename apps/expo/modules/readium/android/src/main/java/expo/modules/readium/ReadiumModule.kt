package expo.modules.readium

import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import org.readium.r2.shared.extensions.toMap
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import android.util.Log
import java.net.URL

class ReadiumModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("Readium")

    AsyncFunction("extractArchive") Coroutine { archiveUrl: String, extractedUrl: String ->
      val context = appContext.reactContext ?: throw Exception("React context is null")
      BookService.getInstance(context).extractArchive(URL(archiveUrl), URL(extractedUrl))
    }

    AsyncFunction("openPublication") Coroutine { bookId: String, publicationUri: String ->
      val context = appContext.reactContext ?: throw Exception("React context is null")
      val publication = BookService.getInstance(context).openPublication(bookId, URL(publicationUri))
      // Return JSON manifest - for now return basic metadata
      mapOf(
        "metadata" to mapOf(
          "title" to publication.metadata.title,
          "author" to publication.metadata.authors.joinToString(", ") { it.name },
          "publisher" to publication.metadata.publishers.joinToString(", ") { it.name },
          "identifier" to (publication.metadata.identifier ?: ""),
          "language" to (publication.metadata.languages.firstOrNull() ?: "en")
        ),
        "readingOrder" to publication.readingOrder.map { link ->
          mapOf(
            "href" to link.href,
            "type" to link.type,
            "title" to (link.title ?: "")
          )
        }
      )
    }

    AsyncFunction("getResource") Coroutine { bookId: String, linkJson: Map<String, Any> ->
      val context = appContext.reactContext ?: throw Exception("React context is null")
      val linkJsonObj = JSONObject(linkJson)
      val link = Link.fromJSON(linkJsonObj) ?: return@Coroutine null
      BookService.getInstance(context).getResource(bookId, link)
    }

    AsyncFunction("getPositions") Coroutine { bookId: String ->
      val context = appContext.reactContext ?: throw Exception("React context is null")
      BookService.getInstance(context).getPositions(bookId)
    }

    AsyncFunction("locateLink") Coroutine { bookId: String, linkJson: Map<String, Any> ->
      val context = appContext.reactContext ?: throw Exception("React context is null")
      val linkJsonObj = JSONObject(linkJson)
      val link = Link.fromJSON(linkJsonObj) ?: return@Coroutine null
      BookService.getInstance(context).locateLink(bookId, link)
    }

    // TODO: Legacy functions - determine if needed
    AsyncFunction("loadEPUB") Coroutine { url: String ->
      try {
        val context = appContext.reactContext ?: throw Exception("React context is null")
        BookService.getInstance(context).openPublication("default", URL(url))
        true
      } catch (e: Exception) {
        false
      }
    }

    AsyncFunction("goToNextPage") { true }
    AsyncFunction("goToPreviousPage") { true }
    AsyncFunction("goToPage") { pageNumber: Int -> true }
    AsyncFunction("getCurrentPage") { 1 }
    AsyncFunction("getTotalPages") { 100 }
    AsyncFunction("updateReaderConfig") { config: Map<String, Any> -> }

    View(ReadiumView::class) {
      Events("onLocatorChange", "onPageChange", "onBookLoaded", "onLayoutChange", "onMiddleTouch", "onSelection", "onDoubleTouch", "onError")

      AsyncFunction("goToLocation") { view: ReadiumView, locatorJson: Map<String, Any> ->
        view.goToLocation(locatorJson)
      }

      AsyncFunction("goForward") { view: ReadiumView ->
        view.goForward()
      }

      AsyncFunction("goBackward") { view: ReadiumView ->
        view.goBackward()
      }

      Prop("bookId") { view: ReadiumView, prop: String ->
        view.setBookId(prop)
      }

      Prop("locator") { view: ReadiumView, prop: Map<String, Any> ->
        view.setLocator(prop)
      }

      Prop("initialLocator") { view: ReadiumView, prop: Map<String, Any> ->
        view.setInitialLocator(prop)
      }

      Prop("url") { view: ReadiumView, prop: String ->
        view.setUrl(prop)
      }

      Prop("colors") { view: ReadiumView, prop: Map<String, String> ->
        view.setColors(prop)
      }

      Prop("fontSize") { view: ReadiumView, prop: Double ->
        view.setFontSize(prop)
      }

      Prop("lineHeight") { view: ReadiumView, prop: Double ->
        view.setLineHeight(prop)
      }

      Prop("fontFamily") { view: ReadiumView, prop: String ->
        view.setFontFamily(prop)
      }

      Prop("readingDirection") { view: ReadiumView, prop: String ->
        view.setReadingDirection(prop)
      }

      OnViewDidUpdateProps { view: ReadiumView ->
        view.finalizeProps()
      }
    }
  }
}
