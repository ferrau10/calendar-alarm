// swift-tools-version:5.9
import PackageDescription

// Platform-independent logic shared by the iOS app (models, alarm times, later the calendar client and
// sync engine). Builds and tests with `swift test` on macOS (and Linux).
let package = Package(
    name: "CalendarAlarmCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "CalendarAlarmCore", targets: ["CalendarAlarmCore"])],
    targets: [
        .target(name: "CalendarAlarmCore"),
        .testTarget(name: "CalendarAlarmCoreTests", dependencies: ["CalendarAlarmCore"]),
    ]
)
