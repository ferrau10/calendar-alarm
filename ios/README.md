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

Without `Secrets.xcconfig` the app builds with placeholders and sign-in fails.

