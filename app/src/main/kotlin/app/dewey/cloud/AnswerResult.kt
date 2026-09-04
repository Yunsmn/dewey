package app.dewey.cloud

/**
 * What came back from asking the cloud model to answer a question.
 *
 * Every way this can fail is named rather than folded into one generic error,
 * because the right thing to tell the user is different for each one: retry
 * later, check your connection, wait for quota to reset, or nothing to do at
 * all because the feature was never set up. A UI that only knows "it failed"
 * cannot say any of that, and the user has explicitly asked that this never
 * come back as a silent empty answer or a crash.
 */
sealed interface AnswerResult {

    data class Answered(val text: String) : AnswerResult

    sealed interface Failure : AnswerResult {

        /** No `google-services.json` at build time. See BuildConfig.HAS_FIREBASE. */
        data object NotConfigured : Failure

        /** The device could not reach the backend at all. */
        data object NoNetwork : Failure

        /** The backend didn't respond in time. */
        data object TimedOut : Failure

        /** This Firebase project's free-tier quota is used up for now. */
        data object QuotaExceeded : Failure

        /**
         * The backend rejected the request rather than answering it — an
         * unattested App Check token, a revoked key, a project missing a step
         * from docs/firebase-setup.md.
         */
        data object NotAuthorized : Failure

        /** The model declined to answer, or stopped partway, on safety grounds. */
        data class Blocked(val reason: String) : Failure

        /** The call succeeded but there was no usable text in the response. */
        data object EmptyResponse : Failure

        /** Anything else, surfaced with whatever the SDK said — never swallowed. */
        data class Unavailable(val reason: String) : Failure
    }
}
