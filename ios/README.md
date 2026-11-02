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
