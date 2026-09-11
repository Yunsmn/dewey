package app.dewey.cloud

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.content
import java.io.IOException
import java.time.LocalDate

/**
 * Answers a question from passages already retrieved on device, through
 * Gemini via Firebase AI Logic's Gemini Developer API backend.
 *
 * Only ever constructed behind `BuildConfig.HAS_FIREBASE` — see
 * [UnconfiguredAnswerComposer] for what runs otherwise. Constructing this
 * class never touches Firebase; only calling [answer] does, and by then a
 * `FirebaseApp` is expected to exist. The model is built per question, not
 * once, because its system instruction carries today's date — see
 * [AnswerPromptBuilder.systemInstruction] — and a model built at launch would
 * go on believing it is that day. Building one is cheap: no network.
 *
 * App Check is what stops anyone who finds this project id from spending its
 * quota — see docs/firebase-setup.md. A rejected token surfaces as
 * [AnswerResult.Failure.NotAuthorized] here, not a crash.
 *
 * ## Latency
 *
 * Measured against the live endpoint on 2026-09-10, because the first version
 * of this class never answered on a device: it waited out the SDK's default
 * three-minute timeout and then reported a timeout. Two things were behind it.
 * The alias it used resolved to a thinking model that took 28s to reply "ok".
 * And the backend's latency is wildly uneven: the same small request took 1.2s,
 * then 52.7s. Streaming did not help — the first chunk arrived with the whole
 * answer. Hence a model that does not think, a short timeout, and one retry,
 * which on a latency that uneven is often faster than waiting.
 */
class GeminiAnswerComposer(
    private val modelName: String = MODEL_NAME,
    private val today: () -> LocalDate = LocalDate::now,
) : AnswerComposer {

    /** One per question, with that day's date in its instructions — see the class doc. */
    private fun modelFor(date: LocalDate): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            systemInstruction = content { text(AnswerPromptBuilder.systemInstruction(date)) },
            requestOptions = RequestOptions(TIMEOUT_MILLIS),
        )

    override suspend fun answer(question: String, passages: List<RetrievedPassage>): AnswerResult {
        // Capped once, here, so the numbers [parseAnswerSources] resolves
        // afterwards refer to the exact same list the prompt showed the
        // model — [AnswerPromptBuilder.build] re-applies [AnswerPromptBuilder.cap]
        // to this, but capping an already-capped list is a no-op.
        val cappedPassages = AnswerPromptBuilder.cap(passages)
        val prompt = AnswerPromptBuilder.build(question, cappedPassages)

        return try {
            // Inside the try: a FirebaseApp that never initialised throws here,
            // and that must become a state like any other failure.
            val model = modelFor(today())
            val response = retryOnceOn(isRetryable = { it is RequestTimeoutException }) {
                model.generateContent(content { text(prompt) })
            }
            val rawText = response.text?.trim()
            if (rawText.isNullOrEmpty()) {
                AnswerResult.Failure.EmptyResponse
            } else {
                val parsed = parseAnswerSources(rawText, cappedPassages.size)
                val citedDocumentIds = parsed.citedPassageNumbers
                    ?.map { number -> cappedPassages[number - 1].documentId }
                    ?.distinct()
                AnswerResult.Answered(text = parsed.text, citedDocumentIds = citedDocumentIds)
            }
        } catch (e: FirebaseAIException) {
            // Logged with its cause before it is reduced to a one-line state.
            // The user sees "no connection" or "took too long"; whoever debugs
            // it needs the exception underneath, which the mapping throws away.
            Log.w(TAG, "Answer request failed: ${e::class.simpleName}", e)
            mapFirebaseAIFailure(e)
        } catch (e: Exception) {
            // The SDK wraps failures inside generateContent as FirebaseAIException,
            // but a FirebaseApp that never initialised, or an App Check provider
            // that throws before the request is even built, can still reach here
            // as something else. It must become a state, not a crash — that is
            // the one thing this class was asked for by name.
            Log.w(TAG, "Answer request failed before reaching the SDK's own handling", e)
            AnswerResult.Failure.Unavailable(e.message ?: e::class.simpleName ?: "unknown error")
        }
    }

    private companion object {
        const val TAG = "GeminiAnswerComposer"

        /**
         * A moving alias rather than a pinned version, deliberately, and the
         * lite one.
         *
         * Moving: verified against the live endpoint on 2026-09-02,
         * `gemini-2.0-flash` is retired and `gemini-2.5-flash` is "no longer
         * available to new users". A pinned name in a repo that judges may
         * build months from now is a runtime NOT_FOUND waiting to happen.
         *
         * Lite: `gemini-flash-latest` thinks before answering, which cost 28s
         * on a one-word reply and 74s on a bill question even with its thinking
         * budget set to zero. The lite model does not think, and answered the
         * same bill question correctly. This class summarises passages it is
         * handed; it has no use for reasoning it has to wait for.
         */
        const val MODEL_NAME = "gemini-flash-lite-latest"

        /**
         * Per attempt, so the worst case is two of these. Short enough that a
         * stuck request becomes a retry the user never sees rather than a
         * spinner; long enough for the slow responses measured above that
         * still came back.
         */
        const val TIMEOUT_MILLIS = 30_000L
    }
}

/**
 * Runs [block], and runs it once more if it fails with something [isRetryable]
 * accepts. A second failure of any kind is thrown as it is.
 *
 * Top-level and `internal` so the retry rule can be unit-tested without a
 * model or a network.
 */
internal suspend fun <T> retryOnceOn(isRetryable: (Throwable) -> Boolean, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        if (!isRetryable(e)) throw e
        block()
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
