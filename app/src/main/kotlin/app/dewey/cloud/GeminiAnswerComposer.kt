package app.dewey.cloud

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.content
import java.io.IOException

/**
 * Answers a question from passages already retrieved on device, through
 * Gemini via Firebase AI Logic's Gemini Developer API backend.
 *
 * Only ever constructed behind `BuildConfig.HAS_FIREBASE` — see
 * [UnconfiguredAnswerComposer] for what runs otherwise. [model] is built
 * lazily so that constructing this class never touches Firebase; only calling
 * [answer] does, and by then a `FirebaseApp` is expected to exist.
 *
 * App Check is what stops anyone who finds this project id from spending its
 * quota — see docs/firebase-setup.md. A rejected token surfaces as
 * [AnswerResult.Failure.NotAuthorized] here, not a crash.
 */
class GeminiAnswerComposer(
    modelName: String = MODEL_NAME,
) : AnswerComposer {

    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName)
    }

    override suspend fun answer(question: String, passages: List<RetrievedPassage>): AnswerResult {
        val prompt = AnswerPromptBuilder.build(question, passages)

        return try {
            val response = model.generateContent(content { text(prompt) })
            val text = response.text?.trim()
            if (text.isNullOrEmpty()) AnswerResult.Failure.EmptyResponse else AnswerResult.Answered(text)
        } catch (e: FirebaseAIException) {
            mapFirebaseAIFailure(e)
        } catch (e: Exception) {
            // The SDK wraps failures inside generateContent as FirebaseAIException,
            // but a FirebaseApp that never initialised, or an App Check provider
            // that throws before the request is even built, can still reach here
            // as something else. It must become a state, not a crash — that is
            // the one thing this class was asked for by name.
            AnswerResult.Failure.Unavailable(e.message ?: e::class.simpleName ?: "unknown error")
        }
    }

    private companion object {
        /**
         * A moving alias rather than a pinned version, deliberately.
         *
         * Verified against the live endpoint on 2026-09-02: `gemini-2.0-flash`
         * is retired and `gemini-2.5-flash` is "no longer available to new
         * users" — two retirements visible on the same day. A pinned name in a
         * repo that judges may build months from now is a runtime NOT_FOUND
         * waiting to happen, and no unit test would catch it because the failure
         * only exists on the wire.
         *
         * The cost is that the model can change under us. For a summariser
         * working from passages we supply, that is the cheaper risk.
         */
        const val MODEL_NAME = "gemini-flash-latest"
    }
}

/**
 * Turns an SDK exception into a state the UI can show.
 *
 * A top-level `internal` function rather than a private method so a unit test
 * can drive every branch directly, without a network call or a FirebaseApp —
 * see GeminiAnswerComposerFailureMappingTest. [FirebaseAIException] is not
 * sealed, so a subtype this has not seen before still needs a sensible
 * answer: it becomes [AnswerResult.Failure.Unavailable] with whatever the SDK
 * said, on the theory that an unfamiliar failure is still better reported
 * than swallowed.
 */
/**
 * The kinds of failure the SDK distinguishes, as plain data.
 *
 * Exists so the decision below can be tested. The SDK's exception constructors
 * are `internal` to its own module, so a test cannot build one — and that is not
 * visible in decompiled bytecode, where Kotlin's `internal` appears public. The
 * type dispatch therefore stays one trivial `when`, and everything that could
 * actually be wrong lives in [classifyFailure], which takes only values a test
 * can supply.
 */
internal enum class FirebaseFailureKind {
    QUOTA_EXCEEDED,
    TIMED_OUT,
    PERMISSION_MISSING,
    PROMPT_BLOCKED,
    RESPONSE_STOPPED,
    UNKNOWN,
    UNRECOGNISED,
}

internal fun kindOf(e: FirebaseAIException): FirebaseFailureKind = when (e) {
    is QuotaExceededException -> FirebaseFailureKind.QUOTA_EXCEEDED
    is RequestTimeoutException -> FirebaseFailureKind.TIMED_OUT
    is PermissionMissingException -> FirebaseFailureKind.PERMISSION_MISSING
    is PromptBlockedException -> FirebaseFailureKind.PROMPT_BLOCKED
    is ResponseStoppedException -> FirebaseFailureKind.RESPONSE_STOPPED
    is UnknownException -> FirebaseFailureKind.UNKNOWN
    else -> FirebaseFailureKind.UNRECOGNISED
}

internal fun classifyFailure(
    kind: FirebaseFailureKind,
    message: String?,
    cause: Throwable?,
): AnswerResult.Failure = when (kind) {
    FirebaseFailureKind.QUOTA_EXCEEDED -> AnswerResult.Failure.QuotaExceeded
    FirebaseFailureKind.TIMED_OUT -> AnswerResult.Failure.TimedOut
    FirebaseFailureKind.PERMISSION_MISSING -> AnswerResult.Failure.NotAuthorized
    FirebaseFailureKind.PROMPT_BLOCKED -> AnswerResult.Failure.Blocked(message ?: "prompt blocked")
    FirebaseFailureKind.RESPONSE_STOPPED -> AnswerResult.Failure.Blocked(message ?: "response stopped")
    FirebaseFailureKind.UNKNOWN ->
        // The SDK's own catch-all: anything it did not recognise, including a raw
        // network failure, arrives here with the original exception as the cause.
        // An IOException cause is the signature of "never reached the backend",
        // which is worth telling the user apart from "the backend refused".
        if (cause is IOException) {
            AnswerResult.Failure.NoNetwork
        } else {
            AnswerResult.Failure.Unavailable(message ?: "cloud request failed")
        }
    FirebaseFailureKind.UNRECOGNISED ->
        AnswerResult.Failure.Unavailable(message ?: "cloud request failed")
}

internal fun mapFirebaseAIFailure(e: FirebaseAIException): AnswerResult.Failure =
    classifyFailure(kindOf(e), e.message, e.cause)
