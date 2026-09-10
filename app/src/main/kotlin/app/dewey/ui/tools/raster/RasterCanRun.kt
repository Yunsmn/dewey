package app.dewey.ui.tools.raster

/**
 * Whether a PDF-to-images run has everything it needs: a document to read and
 * somewhere to put the pages it produces.
 */
fun canRunPdfToImages(hasSource: Boolean, hasDestination: Boolean): Boolean = hasSource && hasDestination

/** Whether an images-to-PDF run has anything to assemble. */
fun canRunImagesToPdf(imageCount: Int): Boolean = imageCount > 0

/** Whether a compress run has a document to shrink. */
fun canRunCompress(hasSource: Boolean): Boolean = hasSource
