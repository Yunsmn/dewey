package app.dewey.pdf

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Encrypting a PDF with a password, and reversing it.
 *
 * Read this before trusting either direction:
 *
 *  - [protect]'s **user password** is real: without it, the page content is
 *    encrypted (AES-128 here) and unreadable to any compliant reader. That is
 *    the one genuine secret this file deals in.
 *  - The permission bits ([AccessPermission] — no printing changes, no
 *    editing, no extraction) are honoured only by *compliant* readers, and
 *    only when the document is opened with the user password rather than the
 *    owner password. They are not encryption. Any tool that ignores them —
 *    qpdf, pikepdf, most "remove PDF restrictions" utilities — strips them in
 *    one command once it already has a password that opens the file, which
 *    the user's own password is. The UI must not describe [protect] as
 *    "preventing printing" or "preventing copying"; it prevents *casual*
 *    disregard of those things by well-behaved software, nothing more.
 *  - [unlock] is the honest complement: given the right password, produce a
 *    plain copy. It is not a recovery tool — a wrong password fails cleanly,
 *    it does not try to break the encryption.
 */

/** What went wrong that [PdfWorkspace.Failure] has no vocabulary for. */
sealed interface ProtectFailure {
    data class WeakPassword(val reason: PasswordCheck) : ProtectFailure
    data object WrongPassword : ProtectFailure
}

/** Carries a [ProtectFailure] through Kotlin's [Result], parallel to [PdfToolException]. */
class ProtectToolException(val failure: ProtectFailure) : Exception(failure.toString())

private const val TAG = "ProtectTools"

/**
 * Encrypts [source] with [password] and writes the result to [target].
 *
 * @param allowPrinting whether a compliant reader lets the document be
 *   printed once opened with [password]. Everything else — modifying,
 *   extracting content, filling forms, assembling pages — is refused, except
 *   accessibility extraction (see below). That default fits this app's
 *   documents: a protected bill or statement is usually protected so it
 *   cannot be casually copied out of or altered, not so it can never be
 *   printed for the same reason the original PDF was printable.
 */
suspend fun protect(
    workspace: PdfWorkspace,
    resolver: ContentResolver,
    source: Uri,
    target: Uri,
    sizeBytes: Long,
    password: String,
    allowPrinting: Boolean = true,
): Result<Unit> {
    val check = ProtectPasswordPolicy.check(password)
    if (check !is PasswordCheck.Valid) {
        return Result.failure(ProtectToolException(ProtectFailure.WeakPassword(check)))
    }

    return workspace.read(source, sizeBytes) { document ->
        try {
            applyProtection(document, password, allowPrinting)
        } catch (e: Exception) {
            // Caught here rather than left to PdfWorkspace.read's own catch:
            // that one reports every failure inside the block as Unreadable,
            // which would be a lie about a document that opened just fine and
            // failed on the way *out*.
            Log.w(TAG, "Could not apply protection to $source", e)
            return@read Result.failure(PdfToolException(PdfWorkspace.Failure.CouldNotWrite(e.message ?: "could not protect")))
        }
        saveOpenDocument(document, resolver, target)
    }.flatten()
}

/**
 * Opens [source] with [password] and writes an unencrypted copy to [target].
 *
 * Deliberately does not go through [PdfWorkspace.read]: it refuses every
 * encrypted document before its block ever runs, which is correct for every
 * other tool in this app and wrong for exactly this one — this is the one
 * place a password is expected and checked. The precautions [PdfWorkspace]
 * exists for are reproduced rather than skipped: [PdfWorkspace.MAX_BYTES] is
 * reused directly, and PDFBox is still told to spill parsed state to
 * [cacheDir] instead of the JVM's default temp directory, which is not
 * reliably writable on Android.
 */
suspend fun unlock(
    resolver: ContentResolver,
    cacheDir: File,
    source: Uri,
    target: Uri,
    sizeBytes: Long,
    password: String,
    io: CoroutineDispatcher = Dispatchers.IO,
): Result<Unit> = withContext(io) {
    if (sizeBytes > PdfWorkspace.MAX_BYTES) {
        return@withContext Result.failure(
            PdfToolException(PdfWorkspace.Failure.TooLarge(source.lastPathSegment.orEmpty()))
        )
    }
    try {
        resolver.openInputStream(source).use { stream ->
            if (stream == null) {
                return@withContext Result.failure(
                    PdfToolException(PdfWorkspace.Failure.Unreadable(source.lastPathSegment.orEmpty()))
                )
            }
            val memoryUsage = MemoryUsageSetting.setupTempFileOnly().setTempDir(cacheDir)
            PDDocument.load(stream, password, memoryUsage).use { document ->
                // The document is already decrypted in memory at this point —
                // PDDocument.load succeeded with this password. Saving it as-is
                // would still carry the encryption dictionary forward; this is
                // what actually produces a plain copy.
                document.setAllSecurityToBeRemoved(true)
                saveOpenDocument(document, resolver, target)
            }
        }
    } catch (e: InvalidPasswordException) {
        // The one wrong-password path PDFBox actually distinguishes. Anything
        // else wrong with the file falls through to the generic Unreadable
        // below rather than being misreported as a password problem.
        Result.failure(ProtectToolException(ProtectFailure.WrongPassword))
    } catch (e: Exception) {
        Log.w(TAG, "Could not unlock $source", e)
        Result.failure(PdfToolException(PdfWorkspace.Failure.Unreadable(source.lastPathSegment.orEmpty())))
    }
}

private fun applyProtection(document: PDDocument, password: String, allowPrinting: Boolean) {
    val permissions = AccessPermission().apply {
        setCanPrint(allowPrinting)
        setCanPrintFaithful(allowPrinting)
        setCanPrintDegraded(false)
        setCanModify(false)
        setCanModifyAnnotations(false)
        setCanFillInForm(false)
        setCanAssembleDocument(false)
        setCanExtractContent(false)
        // The one bit deliberately left on despite "no extraction" above:
        // screen readers rely on it to read the text aloud, and refusing that
        // is refusing accessibility, not confidentiality.
        setCanExtractForAccessibility(true)
    }

    // A random, never-recorded owner password rather than reusing the user's
    // password for both roles. If they were the same string, "owner" access —
    // which ignores every AccessPermission bit above — would be available to
    // anyone who can already open the file, making the permissions pure
    // theatre. This way the permission bits above are the most a compliant
    // reader will ever grant; nobody, including this app, can later open the
    // file as owner and bypass them, because the string that would let them
    // is generated once and discarded.
    val ownerPassword = UUID.randomUUID().toString()
    val policy = StandardProtectionPolicy(ownerPassword, password, permissions)
    // AES-128 rather than the standard handler's RC4 default: RC4-40 in
    // particular is broken well past the point of being a speed bump, and
    // PDFBox's own StandardSecurityHandler still defaults to it for backward
    // compatibility with very old readers this app has no reason to support.
    policy.setEncryptionKeyLength(128)
    policy.setPreferAES(true)
    document.protect(policy)
}

