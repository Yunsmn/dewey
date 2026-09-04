package app.dewey.cloud

/**
 * Stands in for [GeminiAnswerComposer] when the app was built with no
 * `app/google-services.json` — see `BuildConfig.HAS_FIREBASE` and
 * `app.dewey.di.AppContainer`.
 *
 * The free tier of Dewey must work perfectly with zero Firebase configuration.
 * Returning [AnswerResult.Failure.NotConfigured] instead of the caller ever
 * touching a missing default `FirebaseApp` is what makes that true rather
 * than aspirational — there is nothing here for a fresh clone to crash on.
 */
class UnconfiguredAnswerComposer : AnswerComposer {
    override suspend fun answer(question: String, passages: List<RetrievedPassage>): AnswerResult =
        AnswerResult.Failure.NotConfigured
}
