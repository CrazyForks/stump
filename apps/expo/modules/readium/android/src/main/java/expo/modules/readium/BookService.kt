package expo.modules.readium

import android.content.Context
import android.os.Build
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.streamer.Streamer
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

class BookService(private val context: Context) {



    private val streamer: Streamer = Streamer(context)
    private val publications: MutableMap<String, Publication> = mutableMapOf()

    /**
     * Extracts an archive (EPUB) to a directory
     */
    fun extractArchive(archiveUrl: URL, extractedUrl: URL) {
        ZipFile(archiveUrl.path).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        val newFile = File(extractedUrl.path, entry.name)
                        newFile.parentFile?.mkdirs()
                        newFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }

    /**
     * Opens a publication from a local EPUB file or directory
     */
    suspend fun openPublication(bookId: String, url: URL): Publication {
        println("Opening publication for bookId: $bookId at: $url")
        
        val file = File(url.path)
        require(file.exists()) { "File does not exist: ${url.path}" }
        
        val asset = FileAsset(file)
        val publication = streamer.open(asset, allowUserInteraction = false).getOrThrow()
        
        println("Successfully opened publication: ${publication.metadata.title}")
        
        publications[bookId] = publication
        return publication
    }

    /**
     * Gets a publication by book ID
     */
    fun getPublication(bookId: String): Publication? {
        return publications[bookId]
    }

    /**
     * Gets a resource from a publication
     */
    fun getResource(bookId: String, link: Link): Resource {
        val publication = getPublication(bookId)
            ?: throw Exception("Publication for book $bookId not found")
        return publication.get(link)
    }

    /**
     * Gets positions for a publication
     */
    suspend fun getPositions(bookId: String): List<Locator> {
        val publication = getPublication(bookId)
            ?: throw Exception("Publication for book $bookId not found")
        return publication.positions()
    }

    /**
     * Locates a link within a publication
     */
    fun locateLink(bookId: String, link: Link): Locator? {
        val publication = getPublication(bookId) ?: return null
        return publication.locatorFromLink(link)
    }
}
