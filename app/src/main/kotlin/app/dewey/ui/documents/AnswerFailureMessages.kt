package app.dewey.ui.documents

import app.dewey.cloud.AnswerResult

/**
 * What to tell someone who asked a question and did not get an answer.
 *
 * [AnswerResult.Failure] names each way the cloud call can fail so the right
 * caller can react to it; this is the one place that turns each of those into
 * a sentence a non-technical reader can act on. Nothing upstream of this
 * should ever show an enum name or a stack trace — see the type's own KDoc for
 * why that distinction was worth making in the first place.
 */
fun AnswerResult.Failure.plainMessage(): String = when (this) {
    AnswerResult.Failure.NotConfigured -> "Asking questions isn't set up in this build."
    AnswerResult.Failure.NoNetwork -> "No connection right now — check your internet and try again."
    AnswerResult.Failure.TimedOut -> "That took too long. Try asking again."
    AnswerResult.Failure.QuotaExceeded -> "Dewey's had a lot of questions today — try again later."
    AnswerResult.Failure.NotAuthorized -> "Couldn't reach the assistant right now. Try again later."
    is AnswerResult.Failure.Blocked -> "That question couldn't be answered."
    AnswerResult.Failure.EmptyResponse -> "Got nothing back. Try rephrasing the question."
    is AnswerResult.Failure.Unavailable -> "Something went wrong asking that. Try again."
}
