package com.calendaralarm.data.auth.android

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.calendaralarm.data.auth.AuthorizeOutcome
import com.calendaralarm.data.auth.ConsentRequest
import com.calendaralarm.data.auth.ConsentResult
import com.calendaralarm.data.auth.GoogleAuthClient
import com.calendaralarm.data.auth.GoogleAuthRepository
import com.calendaralarm.data.auth.SignInCancelledException
import com.calendaralarm.data.auth.UiHost
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

class ActivityHost(val activity: Activity) : UiHost

/** Launch [pendingIntent] with `ActivityResultContracts.StartIntentSenderForResult`. */
class PendingIntentConsent(val pendingIntent: PendingIntent) : ConsentRequest

class IntentConsentResult(val data: Intent?) : ConsentResult

/**
 * Sign-in with Credential Manager ("Sign in with Google") and Calendar authorization with the
 * Google Identity AuthorizationClient. Needs the OAuth clients from docs/google-setup.md.
 */
class PlayServicesGoogleAuthClient(
    context: Context,
    private val webClientId: String,
) : GoogleAuthClient {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val authorizationClient = Identity.getAuthorizationClient(appContext)

    override suspend fun signIn(host: UiHost): String {
        check(webClientId.isNotBlank()) { "GOOGLE_WEB_CLIENT_ID is not set; see docs/google-setup.md" }
        val activity = (host as ActivityHost).activity
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            credentialManager.getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelledException()
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).id
        }
        throw IllegalStateException("Unexpected credential type ${credential.type}")
    }

    override suspend fun authorize(): AuthorizeOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleAuthRepository.CALENDAR_READONLY_SCOPE)))
            .build()
        val result = authorizationClient.authorize(request).await()
        val pendingIntent = result.pendingIntent
        return if (result.hasResolution() && pendingIntent != null) {
            AuthorizeOutcome.NeedsConsent(PendingIntentConsent(pendingIntent))
        } else {
            AuthorizeOutcome.Authorized(result.accessToken ?: error("Authorization returned no access token"))
        }
    }

    override suspend fun completeConsent(result: ConsentResult): String {
        val data = (result as IntentConsentResult).data ?: throw SignInCancelledException()
        return authorizationClient.getAuthorizationResultFromIntent(data).accessToken
            ?: error("Consent returned no access token")
    }

    override suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
