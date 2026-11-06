# Calendar Alarm for iPhone

Native SwiftUI app (iOS 26+, uses AlarmKit for real alarms). Shared logic lives in the local Swift package
`Packages/CalendarAlarmCore` (models, alarm times; later the calendar client and sync engine).

Build (on a Mac with Xcode 26+):

```bash
brew install xcodegen
```

```bash
cd ios && xcodegen && open CalendarAlarm.xcodeproj
```

Core logic tests: `cd ios/Packages/CalendarAlarmCore && swift test`.

CI (`.github/workflows/ios.yml`) runs both on GitHub's macOS runners for every PR touching `ios/`.

## Google sign-in setup (one-time)

1. Google Cloud → **Google Auth Platform → Clients → Create client → iOS**, bundle ID
   `io.github.ferrau10.calendaralarm`.
2. Copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig` (git-ignored) and fill in the
   client ID and the *iOS URL scheme* (reversed client ID) shown for that client.
3. The same consent screen and `calendar.readonly` scope as Android are used, so no other changes are needed.

Sign-in uses [AppAuth](https://github.com/openid/AppAuth-iOS) (Google's recommended OAuth library for native apps),
which keeps one login per Google account in the Keychain, so several accounts can be connected at once.

Without `Secrets.xcconfig` the app builds with placeholders and sign-in fails.

## TestFlight and the App Store

The **TestFlight** workflow archives, signs (automatic signing via an App Store Connect API key) and uploads
the app. Each run gets a new build number (`CURRENT_PROJECT_VERSION` = run number).

### One-time setup (owner)

1. **Apple Developer Program** ($99/year): https://developer.apple.com/programs/enroll/
2. **Team ID**: developer.apple.com → Account → Membership details → *Team ID*.
3. **App Store Connect → Apps → + New App**: platform iOS, name *Calendar Alarm*, bundle ID
   `io.github.ferrau10.calendaralarm` (register it first under Certificates, Identifiers & Profiles →
   Identifiers if it isn't listed), SKU `calendar-alarm`.
4. **API key**: App Store Connect → Users and Access → Integrations → **App Store Connect API** →
   *Generate API Key*, access **Admin** (needed for automatic signing). Download the `.p8` file (only once!),
   note the *Key ID* and the *Issuer ID*.
5. **Google iOS OAuth client** (see "Google sign-in setup" above).
6. GitHub → repo **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `APP_STORE_CONNECT_KEY_ID` | Key ID |
| `APP_STORE_CONNECT_ISSUER_ID` | Issuer ID |
| `APP_STORE_CONNECT_KEY_P8` | the whole content of the `.p8` file (including the BEGIN/END lines) |
| `APPLE_TEAM_ID` | Team ID |
| `GOOGLE_IOS_CLIENT_ID` | iOS OAuth client ID |
| `GOOGLE_IOS_REVERSED_CLIENT_ID` | its *iOS URL scheme* |

### Each build

GitHub → **Actions → TestFlight → Run workflow** (works from a phone). About 10–15 minutes later the build
appears in App Store Connect → TestFlight. Add yourself as an internal tester and install it with the
**TestFlight** app on your iPhone (iOS 26+).

### App Store release

In App Store Connect: fill in the listing (reuse `play/listing/` texts and the privacy policy URL), screenshots
(6.9" iPhone), App Privacy (*Data Not Collected*), select the build, **Submit for Review**.

