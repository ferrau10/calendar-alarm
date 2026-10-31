package com.calendaralarm.data.auth

import com.calendaralarm.domain.NotAuthorizedException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleAuthRepositoryTest {
    private object Host : UiHost
    private object Consent : ConsentRequest
    private object ConsentOk : ConsentResult

    private class FakeClient : GoogleAuthClient {
        var signInResult: () -> String = { "me@example.com" }
        var authorizeResult: AuthorizeOutcome = AuthorizeOutcome.Authorized("token-1")
        var consentToken: () -> String = { "token-consent" }
        var signedOut = false

        override suspend fun signIn(host: UiHost) = signInResult()
        override suspend fun authorize() = authorizeResult
        override suspend fun completeConsent(result: ConsentResult) = consentToken()
        override suspend fun signOut() { signedOut = true; error("network down") }
    }

    private class MemoryStore(var email: String? = null) : AccountStore {
        override fun load() = email
        override fun save(email: String) { this.email = email }
        override fun clear() { email = null }
    }

    private val client = FakeClient()
    private val store = MemoryStore()
    private val repo = GoogleAuthRepository(client, store)

    @Test
    fun connectWithScopeAlreadyGrantedConnectsImmediately() = runTest {
        assertEquals(ConnectResult.Connected("me@example.com"), repo.connect(Host))
        assertEquals("me@example.com", repo.account.value)
        assertEquals("me@example.com", store.email)
    }

    @Test
    fun connectNeedingConsentConnectsAfterConsent() = runTest {
        client.authorizeResult = AuthorizeOutcome.NeedsConsent(Consent)

        assertEquals(ConnectResult.NeedsConsent(Consent), repo.connect(Host))
        assertNull(repo.account.value) // not connected until consent is granted

        assertEquals(ConnectResult.Connected("me@example.com"), repo.completeConsent(ConsentOk))
        assertEquals("me@example.com", repo.account.value)
    }

    @Test
    fun consentDeniedFails() = runTest {
        client.authorizeResult = AuthorizeOutcome.NeedsConsent(Consent)
        client.consentToken = { throw IllegalStateException("denied") }
        repo.connect(Host)

        assertTrue(repo.completeConsent(ConsentOk) is ConnectResult.Failed)
        assertNull(repo.account.value)
    }

    @Test
    fun completeConsentWithoutConnectFails() = runTest {
        assertTrue(repo.completeConsent(ConsentOk) is ConnectResult.Failed)
    }

    @Test
    fun cancelledPickerIsReportedAsCancelled() = runTest {
        client.signInResult = { throw SignInCancelledException() }
        assertEquals(ConnectResult.Cancelled, repo.connect(Host))
        assertNull(repo.account.value)
    }

    @Test
    fun signInErrorIsReportedAsFailed() = runTest {
        client.signInResult = { throw RuntimeException("no network") }
        assertTrue(repo.connect(Host) is ConnectResult.Failed)
    }

    @Test
    fun rememberedAccountSurvivesRestartAndGetsTokenSilently() = runTest {
        val restarted = GoogleAuthRepository(client, MemoryStore("me@example.com"))
        assertEquals("me@example.com", restarted.account.value)
        assertEquals("token-1", restarted.accessToken())
    }

    @Test
    fun accessTokenWhenNotConnectedThrows() = runTest {
        try {
            repo.accessToken()
            fail("expected NotAuthorizedException")
        } catch (_: NotAuthorizedException) {
        }
    }

    @Test
    fun accessTokenWhenAccessRevokedThrows() = runTest {
        repo.connect(Host)
        client.authorizeResult = AuthorizeOutcome.NeedsConsent(Consent)
        try {
            repo.accessToken()
            fail("expected NotAuthorizedException")
        } catch (_: NotAuthorizedException) {
        }
    }

    @Test
    fun disconnectClearsStateEvenIfSignOutFails() = runTest {
        repo.connect(Host)

        repo.disconnect()

        assertTrue(client.signedOut)
        assertNull(repo.account.value)
        assertNull(store.email)
    }
}
