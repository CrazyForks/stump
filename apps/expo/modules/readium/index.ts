import { ReadiumLink, ReadiumLocator } from './src'
import ReadiumModule from './src/ReadiumModule'

export * from './src/Readium.types'
export { default } from './src/ReadiumModule'
export { default as ReadiumView } from './src/ReadiumView'

export async function locateLink(
	bookId: string,
	link: ReadiumLink,
): Promise<ReadiumLocator | null> {
	const locator = await ReadiumModule.locateLink(bookId, link)
	return locator
}

export async function extractArchive(url: string, destination: string): Promise<void> {
	return ReadiumModule.extractArchive(url, destination)
}

export async function openPublication(bookId: string, url: string): Promise<void> {
	return ReadiumModule.openPublication(bookId, url)
}

export async function getResource(bookId: string, href: string): Promise<string> {
	return ReadiumModule.getResource(bookId, href)
}

// TODO: type this
export async function getPositions(bookId: string): Promise<unknown[]> {
	return ReadiumModule.getPositions(bookId)
}

export async function goToLocation(bookId: string, locator: ReadiumLocator): Promise<void> {
	return ReadiumModule.goToLocation(bookId, locator)
}
