import ExpoModulesCore
import WebKit
import R2Shared
import R2Navigator
import ReadiumAdapterGCDWebServer
import ReadiumInternal

public struct Props {
    var bookId: String?
    var locator: Locator?
    var initialLocator: Locator?
    var url: String?
    var foreground: Color?
    var background: Color?
    var fontFamily: FontFamily?
    var lineHeight: Double?
    var fontSize: Double?
    var textAlign: TextAlignment?
}

public struct FinalizedProps {
    var bookId: String
    var locator: Locator
    var initialLocator: Locator?
    var url: String
    var foreground: Color
    var background: Color
    var fontFamily: FontFamily
    var lineHeight: Double
    var fontSize: Double
    var textAlign: TextAlignment
}

public class EPUBView: ExpoView {
    let onLocatorChange = EventDispatcher()
    let onPageChange = EventDispatcher()
    let onBookLoaded = EventDispatcher()
    let onLayoutChange = EventDispatcher()
    let onMiddleTouch = EventDispatcher()
    let onDoubleTouch = EventDispatcher()
    let onSelection = EventDispatcher()
    let onError = EventDispatcher()

    public var navigator: EPUBNavigatorViewController?

    public var pendingProps: Props = Props()
    public var props: FinalizedProps?

    private var changingResource = false
    private var isInitialized = false

    public func finalizeProps() {
        let oldProps = props

        // Don't proceed if we don't have required props
        guard let bookId = pendingProps.bookId,
              let url = pendingProps.url else {
            return
        }

        let defaultLocator = Locator(
            href: "",
            type: "application/xhtml+xml",
            locations: Locator.Locations()
        )

        props = FinalizedProps(
            bookId: bookId,
            locator: pendingProps.locator ?? oldProps?.locator ?? defaultLocator,
            initialLocator: pendingProps.initialLocator ?? oldProps?.initialLocator,
            url: url,
            foreground: pendingProps.foreground ?? oldProps?.foreground ?? Color(hex: "#111111")!,
            background: pendingProps.background ?? oldProps?.background ?? Color(hex: "#FFFFFF")!,
            fontFamily: pendingProps.fontFamily ?? oldProps?.fontFamily ?? FontFamily(rawValue: "systemFont"),
            lineHeight: pendingProps.lineHeight ?? oldProps?.lineHeight ?? 1.4,
            fontSize: pendingProps.fontSize ?? oldProps?.fontSize ?? 1.0,
            textAlign: pendingProps.textAlign ?? oldProps?.textAlign ?? TextAlignment.justify
        )

        // If this is a new book or first initialization, load the publication
        if props!.bookId != oldProps?.bookId || props!.url != oldProps?.url || !isInitialized {
            Task {
                // TODO: initializeNavigator like with Kt to initialize custom fonts
                await loadPublication()
            }
            return
        }

        // Update navigator if locator changed
        if props!.locator != oldProps?.locator {
            go(locator: props!.locator)
        }

        // Update preferences
        updatePreferences()
    }

    private func loadPublication() async {
        guard let props = props else { return }
        
        do {
            // First check if we need to download and extract the EPUB
            if let url = URL(string: props.url) {
                var publicationUrl = url
                
                // If it's a remote URL, download it first
                if url.scheme == "http" || url.scheme == "https" {
                    publicationUrl = try await downloadEPUB(from: url)
                }
                
                // Open the publication
                let publication = try await BookService.instance.openPublication(for: props.bookId, at: publicationUrl)
                
//                TODO: See warning in Xcode
                DispatchQueue.main.async {
                    self.initializeNavigator(with: publication)
                }
            }
        } catch {
            print("Error loading publication: \(error)")
//            TODO: See warning in Xcode
            DispatchQueue.main.async {
                self.onError([
                    "errorDescription": error.localizedDescription,
                    "failureReason": "Failed to load publication",
                    "recoverySuggestion": "Check the URL and try again"
                ])
            }
        }
    }
    
    // TODO: Prolly don't need this since I decided to download on JS side
    private func downloadEPUB(from url: URL) async throws -> URL {
        let (data, _) = try await URLSession.shared.data(from: url)
        
        let tempDirectory = FileManager.default.temporaryDirectory
        let epubFile = tempDirectory.appendingPathComponent(UUID().uuidString + ".epub")
        
        try data.write(to: epubFile)
        return epubFile
    }

    public func initializeNavigator(with publication: Publication) {
        guard let props = props else { return }

        let resources = Bundle.main.resourceURL!

        let fontFamilyDeclarations = [
            CSSFontFamilyDeclaration(
                fontFamily: FontFamily(rawValue: "systemFont"),
                fontFaces: [
                    CSSFontFace(
                        file: resources.appendingPathComponent("Literata_500Medium.ttf"),
                        style: .normal, weight: .standard(.normal)
                    ),
                ]
            ).eraseToAnyHTMLFontFamilyDeclaration(),
        ]

        do {
            let initialLocation = props.initialLocator ?? props.locator
            let navigator = try EPUBNavigatorViewController(
                publication: publication,
                initialLocation: initialLocation,
                config: .init(
                    preferences: EPUBPreferences(
                        backgroundColor: props.background,
                        fontFamily: props.fontFamily,
                        fontSize: props.fontSize,
                        lineHeight: props.lineHeight,
                        textAlign: props.textAlign,
                        textColor: props.foreground
                    ),
                    defaults: EPUBDefaults(
                        publisherStyles: false
                    ),
                    contentInset: [
                        .compact: (top: 0, bottom: 0),
                        .regular: (top: 0, bottom: 0),
                        .unspecified: (top: 0, bottom: 0)
                    ],
                    fontFamilyDeclarations: fontFamilyDeclarations
                ),
                httpServer: GCDHTTPServer.shared
            )

            navigator.delegate = self
            addSubview(navigator.view)
            self.navigator = navigator
            self.isInitialized = true
            
            onBookLoaded([
                "success": true,
                "bookMetadata": [
                    "title": publication.metadata.title,
                    "author": publication.metadata.authors.map { $0.name }.joined(separator: ", "),
                    "publisher": publication.metadata.publishers.map { $0.name }.joined(separator: ", "),
                    "identifier": publication.metadata.identifier ?? "",
                    "language": publication.metadata.languages.first ?? "en",
                    "totalPages": publication.positions.count,
                    "chapterCount": publication.readingOrder.count
                ]
            ])
            
            emitCurrentLocator()
            
        } catch {
            print("Failed to create Navigator instance: \(error)")
            onError([
                "errorDescription": error.localizedDescription,
                "failureReason": "Failed to create navigator",
                "recoverySuggestion": "Try reloading the publication"
            ])
        }
    }

    public func destroyNavigator() {
        self.navigator?.view.removeFromSuperview()
        self.navigator = nil
        self.isInitialized = false
    }

    func emitCurrentLocator() {
        guard let navigator = navigator,
              let currentLocator = navigator.currentLocation else {
            return
        }
        
        // FIXME: The epubcfi is not getting picked up right
        onLocatorChange(makeJSON([
            "chapterTitle": currentLocator.title ?? "",
            "href": currentLocator.href,
            "type": currentLocator.type,
            "title": encodeIfNotNil(currentLocator.title),
            "locations": encodeIfNotEmpty(currentLocator.locations.json),
            "text": encodeIfNotEmpty(currentLocator.text.json),
            "epubcfi": currentLocator.locations.partialCFI ?? ""
        ]))
    }

    func emitLayoutChange() {
        guard let navigator = navigator else {
            return
        }
        
        // Get the publication to access updated metadata
        let publication = navigator.publication
        
        onLayoutChange([
            "bookMetadata": [
                "title": publication.metadata.title,
                "author": publication.metadata.authors.map { $0.name }.joined(separator: ", "),
                "publisher": publication.metadata.publishers.map { $0.name }.joined(separator: ", "),
                "identifier": publication.metadata.identifier ?? "",
                "language": publication.metadata.languages.first ?? "en",
                "totalPages": publication.positions.count,
                "chapterCount": publication.readingOrder.count
            ]
        ])
    }

    func go(locator: Locator) {
        if locator.href != navigator?.currentLocation?.href {
            changingResource = true
        }
        _ = self.navigator?.go(to: locator, animated: true)
    }
    
    func goToLocation(locator: Locator) {
        go(locator: locator)
    }
    
    func goForward() {
        _ = navigator?.goForward(animated: true) {}
    }
    
    func goBackward() {
        _ = navigator?.goBackward(animated: true) {}
    }
    
    func updatePreferences() {
        guard let props = props else { return }
        
        navigator?.submitPreferences(EPUBPreferences(
            backgroundColor: props.background,
            fontFamily: props.fontFamily,
            fontSize: props.fontSize,
            lineHeight: props.lineHeight,
            textAlign: props.textAlign,
            textColor: props.foreground
        ))
        
        // Emit layout change event after preferences are updated
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.emitLayoutChange()
        }
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        guard let navigatorView = self.navigator?.view else {
            return
        }
        navigatorView.frame = bounds
    }
}

extension EPUBView: EPUBNavigatorDelegate {
    public func navigator(_ navigator: Navigator, locationDidChange locator: Locator) {
        changingResource = false
        emitCurrentLocator()
    }
    
    public func navigator(_ navigator: Navigator, presentError error: NavigatorError) {
        self.onError([
            "errorDescription": error.errorDescription ?? "Unknown error",
            "failureReason": error.failureReason ?? "Navigation failed",
            "recoverySuggestion": error.recoverySuggestion ?? "Try again"
        ])
    }
    
    public func navigator(_ navigator: VisualNavigator, didTapAt point: CGPoint) {
        let navigator = navigator as! EPUBNavigatorViewController
        
        if point.x < self.bounds.maxX * 0.2 {
            _ = navigator.goBackward(animated: true) {}
            return
        }
        if point.x > self.bounds.maxX * 0.8 {
            _ = navigator.goForward(animated: true) {}
            return
        }

        self.onMiddleTouch()
    }
}
