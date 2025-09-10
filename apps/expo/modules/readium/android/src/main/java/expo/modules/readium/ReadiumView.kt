package expo.modules.readium

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

class ReadiumView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val onLoad by EventDispatcher()
  private val onPageChange by EventDispatcher()
  private val onBookLoaded by EventDispatcher()
  private val onLocatorChange by EventDispatcher()
  private val onMiddleTouch by EventDispatcher()
  private val onSelection by EventDispatcher()
  private val onDoubleTouch by EventDispatcher()
  private val onError by EventDispatcher()

  // Container for the EPUB content
  private val containerView = FrameLayout(context).apply {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    setBackgroundColor(android.graphics.Color.WHITE)
  }
  
  private val contentView = TextView(context).apply {
    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
      gravity = android.view.Gravity.CENTER
    }
    text = "EPUB Reader (Android)\nImplementation pending"
    textAlignment = View.TEXT_ALIGNMENT_CENTER
  }
  
  private var bookId: String? = null
  private var url: String? = null
  private var locator: Map<String, Any>? = null
  private var config: Map<String, Any>? = null
  
  private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(e: MotionEvent): Boolean {
      val centerX = width / 2
      if (e.x > centerX - 100 && e.x < centerX + 100) {
        onMiddleTouch(mapOf<String, Any>())
      }
      return true
    }
    
    override fun onDoubleTap(e: MotionEvent): Boolean {
      onDoubleTouch(mapOf(
        "href" to "sample.html",
        "type" to "text/html",
        "locations" to mapOf(
          "progression" to 0.5,
          "position" to 1
        )
      ))
      return true
    }
  })

  init {
    addView(containerView)
    containerView.addView(contentView)
    
    setOnTouchListener { _, event -> 
      gestureDetector.onTouchEvent(event)
      true
    }
  }
  
  fun setBookId(bookId: String) {
    this.bookId = bookId
    updateContent()
  }
  
  fun setUrl(url: String) {
    this.url = url
    updateContent()
  }
  
  fun setLocator(locator: Map<String, Any>) {
    this.locator = locator
    updateContent()
  }
  
  fun setConfig(config: Map<String, Any>) {
    this.config = config
    updateContent()
  }
  
  private fun updateContent() {
    val text = buildString {
      append("EPUB Reader (Android)\n")
      append("Implementation pending\n\n")
      bookId?.let { append("Book ID: $it\n") }
      url?.let { append("URL: $it\n") }
      locator?.let { append("Has locator: true\n") }
      config?.let { append("Has config: true\n") }
    }
    contentView.text = text
  }
  
  override fun onTouchEvent(event: MotionEvent): Boolean {
    return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
  }
}
