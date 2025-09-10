import R2Shared
import R2Streamer
import Foundation
import ZIPFoundation

enum BookServiceError: Error {
    case openFailed(Publication.OpeningError)
    case publicationNotFound
    case restrictedPublication(Error)
    case extractFailed(URL, String)
    case locatorNotInReadingOrder(String, String)
}

public final class BookService {
    /// An instance of R2Streamer's Streamer class, which is used during the actual
    /// opening of a publication
    private let streamer: Streamer

    /// A cache of publications, keyed by their identifier. A publication is added
    /// to the cache when it is opened
    private var publications: [String : Publication] = [:]
    
    /// A singleton instance of the BookService class
    public static let instance = BookService()
    
    /// A callback that is invoked when a publication is opened, provided to the streamer instance
    private static func onCreatePublication(_ mediaType: MediaType,_ manifest: inout Manifest,_ fetcher: inout Fetcher,_ services: inout PublicationServicesBuilder) -> Void {
        // TODO: write me
    }
    
    /// The initializer for the BookService class
    private init() {
        streamer = Streamer(
            onCreatePublication: Self.onCreatePublication
        )
    }

    /// Opens a publication from a local EPUB file or directory
    /// - Parameters:
    ///   - bookID: The identifier for the book
    ///   - url: The URL of the local publication (EPUB file or extracted directory)
    public func openPublication(for bookID: String, at url: URL) async throws -> Publication {
        print("Opening publication for bookID: \(bookID) at: \(url)")
        
        let asset = FileAsset(url: url)
        guard let mediaType = asset.mediaType() else {
            print("Failed to determine media type for: \(url)")
            throw BookServiceError.openFailed(Publication.OpeningError.unsupportedFormat)
        }
        
        print("Opening publication with media type: \(mediaType)")

        let publication = try await withCheckedThrowingContinuation { cont in
          streamer.open(asset: asset, allowUserInteraction: false) { result in
            switch result {
              case .success(let pub):
                print("Successfully opened publication: \(pub.metadata.title)")
                cont.resume(returning: pub)
              case .failure(let error):
                print("Failed to open publication: \(error)")
                cont.resume(throwing: BookServiceError.openFailed(error))
              case .cancelled:
                print("Publication opening was cancelled")
                cont.resume(throwing: CancellationError())
            }
          }
        }

        try validatePublication(publication: publication)
        publications[bookID] = publication

        return publication
    }
    
    /// Extracts an archive (EPUB) to a directory
    /// - Parameters:
    ///   - archiveUrl: The URL of the local archive file
    ///   - extractedUrl: The URL where the archive should be extracted
    public func extractArchive(archiveUrl: URL, extractedUrl: URL) throws {
        print("Extracting archive from: \(archiveUrl) to: \(extractedUrl)")
        let fileManager = FileManager.default

        do {
            try fileManager.createDirectory(at: extractedUrl, withIntermediateDirectories: true, attributes: nil)
            try fileManager.unzipItem(at: archiveUrl, to: extractedUrl)
            print("Successfully extracted archive")
        } catch {
            print("Extract failed: \(error.localizedDescription)")
            throw BookServiceError.extractFailed(archiveUrl, error.localizedDescription)
        }
    }
    
    /// Gets a publication by book ID
    /// - Parameter bookID: The identifier for the book
    /// - Returns: The publication if found, nil otherwise
    public func getPublication(for bookID: String) -> Publication? {
        return publications[bookID]
    }
    
    /// Gets a resource from a publication
    /// - Parameters:
    ///   - bookID: The identifier for the book
    ///   - link: The link to the resource
    /// - Returns: The resource
    public func getResource(for bookID: String, link: Link) throws -> Resource {
        guard let publication = publications[bookID] else {
            throw BookServiceError.publicationNotFound
        }
        return publication.get(link)
    }
    
    /// Gets positions for a publication
    /// - Parameter bookID: The identifier for the book
    /// - Returns: Array of locators representing positions
    public func getPositions(for bookID: String) throws -> [Locator] {
        guard let publication = publications[bookID] else {
            throw BookServiceError.publicationNotFound
        }
        return publication.positions
    }
    
    /// Locates a link within a publication
    /// - Parameters:
    ///   - bookID: The identifier for the book
    ///   - link: The link to locate
    /// - Returns: A locator for the link, if found
    public func locateLink(for bookID: String, link: Link) -> Locator? {
        guard let publication = getPublication(for: bookID) else {
            return nil
        }
        return publication.locate(link)
    }
    
    /// A helper method to assert that a publication is not restricted.
    /// See https://github.com/readium/swift-toolkit/blob/main/docs/Guides/Readium%20LCP.md#using-the-opened-publication
    private func validatePublication(publication: Publication) throws {
        guard !publication.isRestricted else {
            if let error = publication.protectionError {
                throw BookServiceError.restrictedPublication(error)
            } else {
                throw ReadiumError.unknown
            }
        }
    }

}
