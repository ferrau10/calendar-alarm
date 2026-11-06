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

    /** Requests the Calendar read-only scope for [account]; silent when already granted. */
    suspend fun authorize(account: String): AuthorizeOutcome

    /** Returns the access token from a finished consent screen. */
    suspend fun completeConsent(result: ConsentResult): String

    /** Clears the sign-in state (used when the last account is removed). */
    suspend fun signOut()
}

/** Persists the connected accounts' emails, in the order they were added. */
interface AccountStore {
    fun load(): List<String>
    fun save(accounts: List<String>)
}

sealed interface ConnectResult {
    data class Connected(val email: String) : ConnectResult
    /** Launch [request]'s consent screen, then pass its result to [GoogleAuthRepository.completeConsent]. */
    data class NeedsConsent(val request: ConsentRequest) : ConnectResult
    data object Cancelled : ConnectResult
    data class Failed(val error: Throwable) : ConnectResult
}

/** Any number of Google accounts; [connect] adds one (or re-authorizes one already connected). */
class GoogleAuthRepository(
    private val client: GoogleAuthClient,
    private val store: AccountStore,
) : AuthRepository {
    private val _accounts = MutableStateFlow(store.load().distinct())
    override val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    /** Email chosen in [connect] while waiting for the consent screen. */
    private var pendingEmail: String? = null

    suspend fun connect(host: UiHost): ConnectResult = attempt {
        val email = client.signIn(host)
        when (val outcome = client.authorize(email)) {
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

    override suspend fun accessToken(account: String): String {
        if (account !in _accounts.value) throw NotAuthorizedException("$account is not connected")
        return when (val outcome = client.authorize(account)) {
            is AuthorizeOutcome.Authorized -> outcome.accessToken
            // Access was revoked from the Google account settings; the user has to reconnect.
            is AuthorizeOutcome.NeedsConsent -> throw NotAuthorizedException("Calendar access for $account needs to be granted again")
        }
    }

    override suspend fun disconnect(account: String) {
        val remaining = _accounts.value - account
        if (remaining.isEmpty()) {
            try {
                client.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Clearing local state is what matters; the token simply expires.
            }
        }
        if (pendingEmail == account) pendingEmail = null
        store.save(remaining)
        _accounts.value = remaining
    }

    private fun finishConnect(email: String): ConnectResult {
        pendingEmail = null
        if (email !in _accounts.value) {
            val updated = _accounts.value + email
            store.save(updated)
            _accounts.value = updated
        }
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
