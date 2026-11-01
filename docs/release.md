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
