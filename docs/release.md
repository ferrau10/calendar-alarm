# Releasing and installing

Releases are built by GitHub Actions when you push a version tag. The workflow only runs for tags,
so it doesn't use any Actions minutes otherwise.

## One-time setup

### 1. Create a signing key

Keep this file and its passwords safe: Android only accepts updates signed with the same key.

```bash
keytool -genkeypair -v -keystore release.jks -alias calendar-alarm -keyalg RSA -keysize 4096 -validity 10000
```

### 2. Add repository secrets

GitHub → the repository → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `calendar-alarm` (or the alias you chose) |
| `RELEASE_KEY_PASSWORD` | the key password |
| `GOOGLE_WEB_CLIENT_ID` | the Web client ID from [google-setup.md](google-setup.md) |

### 3. Register the release key with Google

Sign-in only works for APKs whose signing certificate Google knows about. Print the SHA-1 and add a
second **Android** OAuth client for it (same package name), as in step 4 of [google-setup.md](google-setup.md):

```bash
keytool -list -v -keystore release.jks -alias calendar-alarm | grep SHA1
```

## Making a release

```bash
git tag v0.1.0
```

```bash
git push origin v0.1.0
```

**Or from a phone/browser:** on GitHub open **Releases → Draft a new release**, type the new tag
(e.g. `v0.2.0`), choose *Create new tag on publish*, target `main`, then **Publish release**.
The workflow attaches the APK to that release.

A few minutes later the APK appears under **Releases** (`calendar-alarm-v0.1.0.apk`), with the R8
mapping file for de-obfuscating crash reports.

## Building a release locally

Export the same values and run Gradle; without them you get an unsigned APK.

```bash
export RELEASE_KEYSTORE_PATH=$PWD/release.jks RELEASE_KEYSTORE_PASSWORD=... RELEASE_KEY_ALIAS=calendar-alarm RELEASE_KEY_PASSWORD=...
```

```bash
./gradlew assembleRelease
```

## Installing on your phone

1. Open the release page on your phone and download the APK.
2. Open it; Android asks to allow installs from your browser ("Install unknown apps") the first time.
3. Install, open Calendar Alarm and follow the setup screens.

To update, install the newer APK over the old one; your selections are kept.

## Google Play

Each release also contains **`calendar-alarm-vX.Y.Z.aab`**, the App Bundle Google Play needs (Play doesn't
accept APKs for new apps).

### Play App Signing (one-time)

Google Play re-signs the app with its own **app signing key**; your `release.jks` becomes the **upload key**.

1. Play Console → your app → **Test and release → App integrity → Play app signing**: choose
   *Use Google-generated key* (the default) and continue.
2. Upload the first `.aab` (e.g. to the closed testing track). Play remembers your upload key from it.
3. Still under **App integrity → App signing**, copy the **SHA-1 certificate fingerprint** of the
   **App signing key**.
4. Google Cloud → **Google Auth Platform → Clients → Create client → Android**, package
   `com.calendaralarm`, that SHA-1. Without this, sign-in fails (`DEVELOPER_ERROR`) in the app installed
   from Google Play, because it's signed with Google's key, not yours.

Keep the existing Android clients too: they're what makes the APK from GitHub Releases work.

### Each update

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`. Play rejects a bundle whose
   `versionCode` was already uploaded.
2. Make the GitHub release as above.
3. Play Console → the testing track (or Production) → **Create new release** → upload the `.aab`
   → add release notes → **Review release → Start rollout**.

