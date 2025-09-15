package expo.modules.readium

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import kotlinx.coroutines.*
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.extensions.toMap
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import java.util.*
import java.net.URL

data class Props(
    var bookId: String? = null,
    var locator: Map<String, Any>? = null,
    var initialLocator: Map<String, Any>? = null,
    var url: String? = null,
    var foreground: String? = null,
    var background: String? = null,
    var fontFamily: String? = null,
    var lineHeight: Double? = null,
    var fontSize: Double? = null,
    var readingDirection: String? = null
)

data class FinalizedProps(
    val bookId: String,
    val locator: Map<String, Any>,
    val initialLocator: Map<String, Any>?,
    val url: String,
    val foreground: String,
    val background: String,
    val fontFamily: String,
    val lineHeight: Double,
    val fontSize: Double,
    val readingDirection: String
)

class ReadiumView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
    // Required for proper layout! Forces Expo to
    // use the Android layout system for this view,
    // rather than React Native's. Without this,
    // the ViewPager and WebViews will be laid out
    // incorrectly
    override val shouldUseAndroidLayout = true

    private val onLocatorChange by EventDispatcher()
    private val onPageChange by EventDispatcher()
    private val onBookLoaded by EventDispatcher()
    private val onLayoutChange by EventDispatcher()
    private val onMiddleTouch by EventDispatcher()
    private val onSelection by EventDispatcher()
    private val onDoubleTouch by EventDispatcher()
    private val onError by EventDispatcher()

    private var navigator: EpubNavigatorFragment? = null
    private var publication: Publication? = null
    private var isInitialized = false
    private var changingResource = false

    val pendingProps = Props()
    private var props: FinalizedProps? = null

    private val containerView = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val placeholderView = TextView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        text = "Loading EPUB..."
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTextColor(Color.BLACK)
    }

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

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Remove initialize call - not needed for singleton pattern
        addView(containerView)
        containerView.addView(placeholderView)

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    fun finalizeProps() {
        val oldProps = props

        // Don't proceed if we don't have required props
        val bookId = pendingProps.bookId ?: return
        val url = pendingProps.url ?: return

        Log.d("ReadiumView", "finalizeProps called with bookId: $bookId, url: $url")

        // Create default locator if none provided
        val defaultLocator = mapOf(
            "href" to "",
            "type" to "application/xhtml+xml",
            "locations" to mapOf<String, Any>()
        )

        props = FinalizedProps(
            bookId = bookId,
            locator = pendingProps.locator ?: oldProps?.locator ?: defaultLocator,
            initialLocator = pendingProps.initialLocator ?: oldProps?.initialLocator,
            url = url,
            foreground = pendingProps.foreground ?: oldProps?.foreground ?: "#111111",
            background = pendingProps.background ?: oldProps?.background ?: "#FFFFFF",
            fontFamily = pendingProps.fontFamily ?: oldProps?.fontFamily ?: "system",
            lineHeight = pendingProps.lineHeight ?: oldProps?.lineHeight ?: 1.4,
            fontSize = pendingProps.fontSize ?: oldProps?.fontSize ?: 1.0,
            readingDirection = pendingProps.readingDirection ?: oldProps?.readingDirection ?: "ltr"
        )

        // If this is a new book or first initialization, load the publication
        if (props!!.bookId != oldProps?.bookId || props!!.url != oldProps?.url || !isInitialized) {
            coroutineScope.launch {
                loadPublication()
            }
            return
        }

        // Update navigator if locator changed
        if (props!!.locator != oldProps?.locator) {
            goToLocator(props!!.locator)
        }
    }

    private suspend fun loadPublication() {
        val currentProps = props ?: return

        try {
            Log.d("ReadiumView", "Starting publication load for bookId: ${currentProps.bookId}, url: ${currentProps.url}")
            val publication = BookService.getInstance(context).openPublication(currentProps.bookId!!, URL(currentProps.url!!))
            this.publication = publication
            Log.d("ReadiumView", "Publication loaded successfully: ${publication.metadata.title}")

            withContext(Dispatchers.Main) {
                // Just show a simple text view for now instead of full navigator
                showSimpleReader(publication)
            }
        } catch (e: Exception) {
            Log.e("ReadiumView", "Error loading publication", e)
            withContext(Dispatchers.Main) {
                placeholderView.text = "Error loading EPUB: ${e.message}"
                onError(mapOf(
                    "errorDescription" to (e.message ?: "Unknown error"),
                    "failureReason" to "Failed to load publication",
                    "recoverySuggestion" to "Check the URL and try again"
                ))
            }
        }
    }

    private fun showSimpleReader(publication: Publication) {
        placeholderView.text = """
            EPUB Loaded Successfully!
            
            Title: ${publication.metadata.title ?: "Unknown"}
            Author: ${publication.metadata.authors.joinToString(", ") { it.name }}
            Chapters: ${publication.readingOrder.size}
            
            (Full navigator implementation coming soon)
        """.trimIndent()

        isInitialized = true

        // Emit book loaded event
        onBookLoaded(mapOf(
            "success" to true,
            "bookMetadata" to mapOf(
                "title" to publication.metadata.title,
                "author" to publication.metadata.authors.joinToString(", ") { it.name },
                "publisher" to publication.metadata.publishers.joinToString(", ") { it.name },
                "identifier" to (publication.metadata.identifier ?: ""),
                "language" to (publication.metadata.languages.firstOrNull() ?: "en"),
                "totalPages" to 100, // placeholder
                "chapterCount" to publication.readingOrder.size
            )
        ))
    }

    fun goToLocation(locatorJson: Map<String, Any>) {
        Log.d("ReadiumView", "goToLocation called with: $locatorJson")
    }

    fun goForward() {
        Log.d("ReadiumView", "goForward called")
    }

    fun goBackward() {
        Log.d("ReadiumView", "goBackward called")
    }

    private fun goToLocator(locatorMap: Map<String, Any>) {
        Log.d("ReadiumView", "goToLocator called with: $locatorMap")
    }

    // Prop setters
    fun setBookId(bookId: String) {
        pendingProps.bookId = bookId
    }

    fun setLocator(locator: Map<String, Any>) {
        pendingProps.locator = locator
    }

    fun setInitialLocator(initialLocator: Map<String, Any>) {
        pendingProps.initialLocator = initialLocator
    }

    fun setUrl(url: String) {
        pendingProps.url = url
    }

    fun setColors(colors: Map<String, String>) {
        pendingProps.background = colors["background"]
        pendingProps.foreground = colors["foreground"]
    }

    fun setFontSize(fontSize: Double) {
        pendingProps.fontSize = fontSize / 16.0 // Normalize to scale
    }

    fun setLineHeight(lineHeight: Double) {
        pendingProps.lineHeight = lineHeight
    }

    fun setFontFamily(fontFamily: String) {
        pendingProps.fontFamily = fontFamily
    }

    fun setReadingDirection(readingDirection: String) {
        pendingProps.readingDirection = readingDirection
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }
}
