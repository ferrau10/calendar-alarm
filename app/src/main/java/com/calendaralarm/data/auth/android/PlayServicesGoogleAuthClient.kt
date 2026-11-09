package com.calendaralarm.data.auth.android

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.calendaralarm.data.auth.AuthorizeOutcome
import com.calendaralarm.data.auth.ConsentRequest
import com.calendaralarm.data.auth.ConsentResult
import com.calendaralarm.data.auth.GoogleAuthClient
import com.calendaralarm.data.auth.GoogleAuthRepository
import com.calendaralarm.data.auth.SignInCancelledException
import com.calendaralarm.data.auth.UiHost
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
        // 1) One Tap account chooser. It reports "no credential" not only when the phone has no Google
        //    account, but also when it has nothing it may offer to this app (common on some devices).
        val oneTap = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val credential = try {
            getCredential(activity, oneTap)
        } catch (e: NoCredentialException) {
            // 2) Google's recommended fallback: the full "Sign in with Google" screen, which lists every
            //    account on the phone and lets the user add one.
            try {
                getCredential(activity, GetSignInWithGoogleOption.Builder(webClientId).build())
            } catch (fallback: NoCredentialException) {
                throw IllegalStateException(
                    "Google sign-in isn't available for this app right now. " +
                        "Please make sure a Google account is added in Settings → Accounts, then try again. " +
                        "(${fallback.type})",
                    fallback,
                )
            }
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).id
        }
        throw IllegalStateException("Unexpected credential type ${credential.type}")
    }

    private suspend fun getCredential(activity: Activity, option: CredentialOption): Credential = try {
        credentialManager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
    } catch (e: GetCredentialCancellationException) {
        throw SignInCancelledException()
    } catch (e: GetCredentialException) {
        if (e is NoCredentialException) throw e
        // Surface Google's own message (e.g. "Developer console is not set up correctly") instead of hiding it.
        throw IllegalStateException("Google sign-in failed: ${e.message ?: e.type}", e)
    }

    override suspend fun authorize(account: String): AuthorizeOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleAuthRepository.CALENDAR_READONLY_SCOPE)))
            .setAccount(Account(account, ACCOUNT_TYPE))
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
        // A closed or refused permission screen used to look like a silent "cancel", which sent people back to
        // the Connect screen in a loop. Always explain what happened instead.
        val data = (result as IntentConsentResult).data ?: throw IllegalStateException(CONSENT_NOT_GRANTED)
        val authorization = try {
            authorizationClient.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) throw IllegalStateException(CONSENT_NOT_GRANTED, e)
            throw IllegalStateException("Google refused calendar access (code ${e.statusCode}: ${e.message}).", e)
        }
        return authorization.accessToken ?: throw IllegalStateException(CONSENT_NOT_GRANTED)
    }

    override suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private companion object {
        const val CONSENT_NOT_GRANTED =
            "Calendar access wasn't granted. If Google showed \"Google hasn't verified this app\", tap " +
                "\"Advanced\" → \"Go to Calendar Alarm\", then allow access to your calendar."
        const val ACCOUNT_TYPE = "com.google"
    }
}
