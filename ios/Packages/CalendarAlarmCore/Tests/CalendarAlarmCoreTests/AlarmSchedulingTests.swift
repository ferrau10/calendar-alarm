import XCTest
@testable import CalendarAlarmCore

final class AlarmSchedulingTests: XCTestCase {
    func testAlarmUUIDIsStablePerKey() {
        XCTAssertEqual(alarmUUID(forKey: "e1@5"), alarmUUID(forKey: "e1@5"))
    }

    func testDifferentKeysGetDifferentUUIDs() {
        let keys = (0..<2000).flatMap { ["event\($0)@5", "event\($0)@45"] }
        XCTAssertEqual(Set(keys.map(alarmUUID(forKey:))).count, keys.count)
    }

    func testAlarmLabel() {
        XCTAssertEqual(alarmLabel(title: "Standup", leadMinutes: 5), "Standup · in 5 min")
        XCTAssertEqual(alarmLabel(title: "Standup", leadMinutes: 0), "Standup")
    }
}
