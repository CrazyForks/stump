package expo.modules.readium

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ReadiumModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('Readium')` in JavaScript.
    Name("Readium")

    // Defines event names that the module can send to JavaScript.
    Events("onChange", "onPageChange", "onBookLoaded", "onLocatorChange", "onMiddleTouch", "onSelection", "onDoubleTouch", "onError")

    // Async functions to match iOS implementation
    AsyncFunction("extractArchive") { url: String, destination: String ->
      // TODO: Make me plz
      // For now, return success
    }
    
    AsyncFunction("openPublication") { bookId: String, url: String ->
      // TODO: Make me plz
      // For now, return success
    }
    
    AsyncFunction("getResource") { bookId: String, href: String ->
      // TODO: Make me plz
      // For now, return empty string
      ""
    }
    
    AsyncFunction("getPositions") { bookId: String ->
      // TODO: Make me plz
      // For now, return empty array
      emptyList<Map<String, Any>>()
    }
    
    AsyncFunction("locateLink") { bookId: String, href: String ->
      // TODO: Make me plz
      // For now, return empty map
      emptyMap<String, Any>()
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(ReadiumView::class) {
      // Define props that match iOS implementation
      Prop("bookId") { view: ReadiumView, bookId: String ->
        view.setBookId(bookId)
      }
      
      Prop("url") { view: ReadiumView, url: String ->
        view.setUrl(url)
      }
      
      Prop("locator") { view: ReadiumView, locator: Map<String, Any> ->
        view.setLocator(locator)
      }
      
      Prop("config") { view: ReadiumView, config: Map<String, Any> ->
        view.setConfig(config)
      }
      
      // Defines events that the view can send to JavaScript.
      Events("onLoad", "onPageChange", "onBookLoaded", "onLocatorChange", "onMiddleTouch", "onSelection", "onDoubleTouch", "onError")
    }
  }
}
