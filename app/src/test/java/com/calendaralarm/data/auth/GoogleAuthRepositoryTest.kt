package com.calendaralarm.data.auth

import com.calendaralarm.domain.NotAuthorizedException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleAuthRepositoryTest {
    private object Host : UiHost
    private object Consent : ConsentRequest
    private object ConsentOk : ConsentResult

    private class FakeClient : GoogleAuthClient {
        var nextSignIn: () -> String = { "me@example.com" }
        var needsConsent = mutableSetOf<String>()
        val authorizedFor = mutableListOf<String>()
        var consentToken: () -> String = { "token-consent" }
        var signedOut = false

        override suspend fun signIn(host: UiHost) = nextSignIn()
        override suspend fun authorize(account: String): AuthorizeOutcome {
            authorizedFor += account
            return if (account in needsConsent) AuthorizeOutcome.NeedsConsent(Consent) else AuthorizeOutcome.Authorized("token-$account")
        }
        override suspend fun completeConsent(result: ConsentResult) = consentToken()
        override suspend fun signOut() { signedOut = true; error("network down") }
    }

    private class MemoryStore(var accounts: List<String> = emptyList()) : AccountStore {
        override fun load() = accounts
        override fun save(accounts: List<String>) { this.accounts = accounts }
    }

    private val client = FakeClient()
    private val store = MemoryStore()
    private val repo = GoogleAuthRepository(client, store)

    @Test
    fun connectingTwoAccountsKeepsBothInOrder() = runTest {
        assertEquals(ConnectResult.Connected("me@example.com"), repo.connect(Host))
        client.nextSignIn = { "work@example.com" }
        assertEquals(ConnectResult.Connected("work@example.com"), repo.connect(Host))

        assertEquals(listOf("me@example.com", "work@example.com"), repo.accounts.value)
        assertEquals(listOf("me@example.com", "work@example.com"), store.accounts)
    }

    @Test
    fun connectingAnAlreadyConnectedAccountDoesNotDuplicateIt() = runTest {
        repo.connect(Host)
        repo.connect(Host)
        assertEquals(listOf("me@example.com"), repo.accounts.value)
    }

    @Test
    fun eachAccountGetsItsOwnToken() = runTest {
        repo.connect(Host)
        client.nextSignIn = { "work@example.com" }
        repo.connect(Host)

        assertEquals("token-work@example.com", repo.accessToken("work@example.com"))
        assertEquals("token-me@example.com", repo.accessToken("me@example.com"))
    }

    @Test
    fun accountNeedingConsentIsAddedAfterConsent() = runTest {
        client.needsConsent += "me@example.com"
        assertEquals(ConnectResult.NeedsConsent(Consent), repo.connect(Host))
        assertTrue(repo.accounts.value.isEmpty())

        assertEquals(ConnectResult.Connected("me@example.com"), repo.completeConsent(ConsentOk))
        assertEquals(listOf("me@example.com"), repo.accounts.value)
    }

    @Test
    fun consentDeniedAddsNothing() = runTest {
        client.needsConsent += "me@example.com"
        client.consentToken = { throw IllegalStateException("denied") }
        repo.connect(Host)
        assertTrue(repo.completeConsent(ConsentOk) is ConnectResult.Failed)
        assertTrue(repo.accounts.value.isEmpty())
    }

    @Test
    fun cancelledPickerIsReportedAsCancelled() = runTest {
        client.nextSignIn = { throw SignInCancelledException() }
        assertEquals(ConnectResult.Cancelled, repo.connect(Host))
    }

    @Test
    fun accountsSurviveRestart() = runTest {
        val restarted = GoogleAuthRepository(client, MemoryStore(listOf("me@example.com", "work@example.com")))
        assertEquals(listOf("me@example.com", "work@example.com"), restarted.accounts.value)
        assertEquals("token-work@example.com", restarted.accessToken("work@example.com"))
    }

    @Test
    fun tokenForUnknownOrRevokedAccountThrows() = runTest {
        repo.connect(Host)
        expectNotAuthorized { repo.accessToken("stranger@example.com") }
        client.needsConsent += "me@example.com"
        expectNotAuthorized { repo.accessToken("me@example.com") }
    }

    @Test
    fun removingOneAccountKeepsTheOtherAndDoesNotSignOut() = runTest {
        repo.connect(Host)
        client.nextSignIn = { "work@example.com" }
        repo.connect(Host)

        repo.disconnect("me@example.com")

        assertEquals(listOf("work@example.com"), repo.accounts.value)
        assertEquals(listOf("work@example.com"), store.accounts)
        assertFalse(client.signedOut)
    }

    @Test
    fun removingTheLastAccountSignsOutEvenIfThatFails() = runTest {
        repo.connect(Host)
        repo.disconnect("me@example.com")
        assertTrue(repo.accounts.value.isEmpty())
        assertTrue(client.signedOut)
    }

    private suspend fun expectNotAuthorized(block: suspend () -> Unit) {
        try {
            block()
            fail("expected NotAuthorizedException")
        } catch (_: NotAuthorizedException) {
        }
    }
}
