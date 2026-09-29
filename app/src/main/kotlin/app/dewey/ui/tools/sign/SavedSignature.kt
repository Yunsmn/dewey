package app.dewey.ui.tools.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Remembering the last signature drawn, so a person who signs several
 * documents in a row doesn't have to redraw it every time.
 *
 * Saved as a PNG rather than JPEG — [PNG][Bitmap.CompressFormat.PNG] is the
 * one format here that keeps the transparent background a signature needs,
 * the same reason [app.dewey.pdf.RasterImageFormat.PNG] exists — in the app's
 * own files directory rather than the cache, which the system is free to
 * clear under storage pressure; a signature someone drew once is worth
 * treating as a kept file, not a scratch one.
 */
private const val SIGNATURE_FILE_NAME = "saved_signature.png"
private const val TAG = "SavedSignature"

private fun signatureFile(filesDir: File): File = File(filesDir, SIGNATURE_FILE_NAME)

/** Whether a signature from a previous session is available to reuse. */
fun hasSavedSignature(filesDir: File): Boolean = signatureFile(filesDir).exists()

/** Overwrites the remembered signature with [bitmap]. */
suspend fun saveSignature(filesDir: File, bitmap: Bitmap): Result<Unit> = withContext(Dispatchers.IO) {
    // Written beside the real file and renamed over it, so a crash mid-write
    // leaves the old signature rather than a truncated PNG that still counts
    // as "saved" but decodes to nothing.
    val partial = File(filesDir, "$SIGNATURE_FILE_NAME.partial")
    try {
        val written = partial.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
        check(written) { "PNG encoder refused the signature" }
        check(partial.renameTo(signatureFile(filesDir))) { "could not replace the saved signature" }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.w(TAG, "Could not save the signature", e)
        partial.delete()
        Result.failure(e)
    }
}

/** The remembered signature, or null if none was ever saved or it could no longer be read. */
suspend fun loadSavedSignature(filesDir: File): Bitmap? = withContext(Dispatchers.IO) {
    val file = signatureFile(filesDir)
    if (!file.exists()) return@withContext null
    try {
        // decodeFile returns null rather than throwing on a damaged file;
        // forget it, so "Use saved signature" stops being offered for nothing.
        BitmapFactory.decodeFile(file.absolutePath) ?: run {
            file.delete()
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the saved signature", e)
        null
    }
}

// PNG is lossless; the value is ignored by Bitmap.compress but still required.
private const val PNG_QUALITY = 100
