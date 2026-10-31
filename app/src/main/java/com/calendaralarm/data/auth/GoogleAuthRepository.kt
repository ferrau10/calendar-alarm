package com.calendaralarm.data.auth

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.NotAuthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the UI runs the sign-in flow (Android: wraps an Activity). Opaque to this layer. */
interface UiHost

/** A consent screen the UI must show (Android: wraps a PendingIntent). Opaque to this layer. */
interface ConsentRequest

/** What the consent screen returned (Android: wraps the result Intent). Opaque to this layer. */
interface ConsentResult

/** The user closed the account picker. */
class SignInCancelledException : Exception("Sign-in cancelled")

sealed interface AuthorizeOutcome {
    data class Authorized(val accessToken: String) : AuthorizeOutcome
    data class NeedsConsent(val request: ConsentRequest) : AuthorizeOutcome
}

/** Thin wrapper over Google sign-in + authorization so the state handling can be unit tested. */
interface GoogleAuthClient {
    /** Lets the user pick a Google account; returns its email. @throws SignInCancelledException */
    suspend fun signIn(host: UiHost): String

    /** Requests the Calendar read-only scope; silent when already granted. */
    suspend fun authorize(): AuthorizeOutcome

    /** Returns the access token from a finished consent screen. */
    suspend fun completeConsent(result: ConsentResult): String

    suspend fun signOut()
}

/** Persists the connected account's email. */
interface AccountStore {
    fun load(): String?
    fun save(email: String)
    fun clear()
}

sealed interface ConnectResult {
    data class Connected(val email: String) : ConnectResult
    /** Launch [request]'s consent screen, then pass its result to [GoogleAuthRepository.completeConsent]. */
    data class NeedsConsent(val request: ConsentRequest) : ConnectResult
    data object Cancelled : ConnectResult
    data class Failed(val error: Throwable) : ConnectResult
}

class GoogleAuthRepository(
    private val client: GoogleAuthClient,
    private val store: AccountStore,
) : AuthRepository {
    private val _account = MutableStateFlow(store.load())
    override val account: StateFlow<String?> = _account.asStateFlow()

    /** Email chosen in [connect] while waiting for the consent screen. */
    private var pendingEmail: String? = null

    suspend fun connect(host: UiHost): ConnectResult = attempt {
        val email = client.signIn(host)
        when (val outcome = client.authorize()) {
            is AuthorizeOutcome.Authorized -> finishConnect(email)
            is AuthorizeOutcome.NeedsConsent -> {
                pendingEmail = email
                ConnectResult.NeedsConsent(outcome.request)
            }
        }
    }

    suspend fun completeConsent(result: ConsentResult): ConnectResult = attempt {
        val email = pendingEmail ?: return@attempt ConnectResult.Failed(IllegalStateException("No sign-in in progress"))
        client.completeConsent(result)
        finishConnect(email)
    }

    override suspend fun accessToken(): String {
        if (_account.value == null) throw NotAuthorizedException()
        return when (val outcome = client.authorize()) {
            is AuthorizeOutcome.Authorized -> outcome.accessToken
            // Access was revoked from the Google account settings; the user has to reconnect.
            is AuthorizeOutcome.NeedsConsent -> throw NotAuthorizedException("Calendar access needs to be granted again")
        }
    }

    override suspend fun disconnect() {
        try {
            client.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Clearing local state is what matters; the token simply expires.
        }
        pendingEmail = null
        store.clear()
        _account.value = null
    }

    private fun finishConnect(email: String): ConnectResult {
        pendingEmail = null
        store.save(email)
        _account.value = email
        return ConnectResult.Connected(email)
    }

    private inline fun attempt(block: () -> ConnectResult): ConnectResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: SignInCancelledException) {
        ConnectResult.Cancelled
    } catch (e: Exception) {
        ConnectResult.Failed(e)
    }

    companion object {
        const val CALENDAR_READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    }
}
