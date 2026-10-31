# Google Cloud setup

Calendar Alarm reads your Google Calendar with the **read-only** scope. To build the app yourself you need a
(free) Google Cloud project with two OAuth client IDs. This takes about 10 minutes and only has to be done once.

> You only need this if you build the app from source. Nothing here costs money.

## 1. Create a project and enable the Calendar API

1. Open the [Google Cloud console](https://console.cloud.google.com/) and sign in with the Google account
   whose calendar you want to use.
2. Project picker (top bar) → **New project** → name it `calendar-alarm` → **Create**, then select it.
3. Go to **APIs & Services → Library**, search for **Google Calendar API**, open it and click **Enable**.

## 2. Configure the OAuth consent screen

Go to **Google Auth Platform** (formerly *APIs & Services → OAuth consent screen*) and click **Get started**:

1. **App information**: app name `Calendar Alarm`, your email as support email.
2. **Audience**: choose **External**.
3. **Contact information**: your email. Accept the policy and **Create**.
4. **Audience → Test users → Add users**: add the Google account(s) that will use the app.
   While the app is in *Testing* status only these accounts can sign in, which is fine for personal use.
5. **Data access → Add or remove scopes**: add
   `https://www.googleapis.com/auth/calendar.readonly` and **Save**.

You do **not** need to publish or verify the app for personal use.

## 3. Get the SHA-1 fingerprint of your signing key

Google ties the Android client to the certificate the APK is signed with.

**Debug builds** (what `./gradlew assembleDebug` and Android Studio produce):

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

Alternatively, from the project root:

```bash
./gradlew signingReport
```

**Release builds**: run the same `keytool` command against your release keystore and register that SHA-1 as
well. Each signing key needs its own Android client.

## 4. Create the Android OAuth client

**Google Auth Platform → Clients → Create client**:

- Application type: **Android**
- Name: `Calendar Alarm (debug)`
- Package name: `com.calendaralarm`
- SHA-1 certificate fingerprint: the value from step 3

Click **Create**. There is nothing to copy from this client; its existence is what allows the app to obtain
tokens. Repeat for the release key if you have one.

## 5. Create the Web OAuth client

The Android sign-in library (Credential Manager) needs a **Web** client ID as its `serverClientId`, even
though there is no server.

**Clients → Create client**:

- Application type: **Web application**
- Name: `Calendar Alarm (server client id)`
- No redirect URIs or origins needed.

Click **Create** and copy the **Client ID** (looks like `1234567890-abc….apps.googleusercontent.com`).

## 6. Put the Web client ID in `local.properties`

Add this line to `local.properties` in the project root (the file is git-ignored, never commit it):

```properties
GOOGLE_WEB_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
```

The build exposes it to the app as `BuildConfig.GOOGLE_WEB_CLIENT_ID`. Builds without it (such as CI) still
compile; sign-in just won't work in them.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `DEVELOPER_ERROR` / error code 10 when signing in | Package name or SHA-1 of the Android client doesn't match the APK you're running (for example debug vs release key). |
| "Access blocked: app has not completed the Google verification process" | Your account isn't listed under **Test users**. |
| "No credentials available" | No Google account on the phone, or `GOOGLE_WEB_CLIENT_ID` is missing or wrong (it must be the **Web** client ID, not the Android one). |
| Calendar requests return 403 `accessNotConfigured` | The Google Calendar API isn't enabled in the project (step 1). |
