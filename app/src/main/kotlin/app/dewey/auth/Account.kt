package app.dewey.auth

import android.util.Log
import app.dewey.BuildConfig
import app.dewey.billing.Entitlements
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Who, if anyone, is signed in on this phone. */
sealed interface AccountState {
    /** This build has no Firebase project, so there is nothing to sign in to. The account section is hidden. */
    data object Unavailable : AccountState

    data object SignedOut : AccountState

    data class SignedIn(val email: String) : AccountState
}

/**
 * An optional account: an email and password, through Firebase Authentication.
 *
 * Optional on purpose. Every feature works signed out, and nothing about a
 * person's documents is tied to the account — they stay on the phone either
 * way. What an account buys is a stable identity for the purchase: signing in
 * makes the RevenueCat customer this account's user id rather than an
 * anonymous id minted for this install, so Librarian follows the person to a
 * new phone or a reinstall instead of needing a store restore. See
 * [Entitlements.identify].
 *
 * Firebase is already how the assistant reaches its model, so this adds no
 * new service, and it is gated on the same BuildConfig.HAS_FIREBASE: a clean
 * clone with no google-services.json never touches FirebaseAuth, which would
 * throw without an initialised FirebaseApp.
 */
class Account(private val entitlements: Entitlements) {

    private val auth: FirebaseAuth? =
        if (BuildConfig.HAS_FIREBASE) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: IllegalStateException) {
                // google-services.json was present at build time but the app
                // did not initialise from it — treat it the same as no project.
                Log.w(TAG, "Firebase is not initialised; accounts are unavailable", e)
                null
            }
        } else {
            null
        }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** The signed-in user's id, handed to RevenueCat when it is configured at startup. */
    val userId: String? get() = auth?.currentUser?.uid

    init {
        auth?.addAuthStateListener { _state.value = readState() }
    }

    /** Null on success, otherwise a sentence to show the person. */
    suspend fun signIn(email: String, password: String): String? =
        authenticate(email, password) { it.signInWithEmailAndPassword(email.trim(), password) }

    /** Null on success, otherwise a sentence to show the person. */
    suspend fun createAccount(email: String, password: String): String? =
        authenticate(email, password) { it.createUserWithEmailAndPassword(email.trim(), password) }

    /** Null on success, otherwise a sentence to show the person. */
    suspend fun sendPasswordReset(email: String): String? {
        val auth = auth ?: return UNAVAILABLE_MESSAGE
        if (email.isBlank()) return "Enter your email first."
        return try {
            auth.sendPasswordResetEmail(email.trim()).awaitTask()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageFor(e)
        }
    }

    /**
     * Signs out of the account and hands the purchase back to an anonymous
     * RevenueCat customer, so the next person on this phone does not inherit
     * the last one's subscription.
     */
    suspend fun signOut() {
        val auth = auth ?: return
        auth.signOut()
        entitlements.forget()
    }

    private suspend fun authenticate(
        email: String,
        password: String,
        call: (FirebaseAuth) -> Task<com.google.firebase.auth.AuthResult>,
    ): String? {
        val auth = auth ?: return UNAVAILABLE_MESSAGE
        if (email.isBlank() || password.isEmpty()) return "Enter an email and a password."
        return try {
            val user = call(auth).awaitTask().user ?: return GENERIC_MESSAGE
            entitlements.identify(userId = user.uid, email = user.email)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Authentication failed", e)
            messageFor(e)
        }
    }

    private fun readState(): AccountState {
        val auth = auth ?: return AccountState.Unavailable
        val user = auth.currentUser ?: return AccountState.SignedOut
        return AccountState.SignedIn(email = user.email.orEmpty())
    }

    private companion object {
        const val TAG = "Account"
        const val UNAVAILABLE_MESSAGE = "Accounts aren't available in this build."
        const val GENERIC_MESSAGE = "Couldn't sign in just now. Try again."

        /**
         * Firebase's exceptions as something a person can act on.
         *
         * With email-enumeration protection on, which new Firebase projects
         * have by default, a wrong password and an unknown email are the same
         * invalid-credentials error — deliberately, so the wording does not
         * claim to know which it was.
         */
        fun messageFor(e: Exception): String = when (e) {
            is FirebaseAuthWeakPasswordException -> "Use a password of at least 6 characters."
            is FirebaseAuthUserCollisionException -> "There's already an account with that email. Sign in instead."
            is FirebaseAuthInvalidUserException -> "That email and password don't match an account."
            is FirebaseAuthInvalidCredentialsException -> "That email and password don't match an account."
            is FirebaseNetworkException -> "No connection. Try again when you're online."
            is FirebaseTooManyRequestsException -> "Too many attempts. Wait a minute and try again."
            else -> GENERIC_MESSAGE
        }
    }
}

/**
 * A Play services [Task] as a suspending call. Written here rather than
 * pulling in kotlinx-coroutines-play-services for one function.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
