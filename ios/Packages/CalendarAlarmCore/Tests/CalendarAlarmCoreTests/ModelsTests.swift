import XCTest
@testable import CalendarAlarmCore

final class ModelsTests: XCTestCase {
    private let start = ISO8601DateFormatter().date(from: "2026-11-02T10:00:00Z")!

    private func event(_ lead: [Int]?) -> SelectedEvent {
        SelectedEvent(eventId: "e1", calendarId: "primary", title: "Standup", start: start, leadMinutes: lead)
    }

    func testDefaultLeadTimeGivesOneAlarm() {
        let alarms = event(nil).alarms(default: defaultLeadMinutes)
        XCTAssertEqual(alarms.map(\.key), ["e1@5"])
        XCTAssertEqual(alarms.first?.at, start.addingTimeInterval(-300))
    }

    func testSeveralLeadTimesEarliestFirstWithoutDuplicates() {
        let alarms = event([5, 45, 5]).alarms(default: defaultLeadMinutes)
        XCTAssertEqual(alarms.map(\.key), ["e1@45", "e1@5"])
        XCTAssertEqual(alarms.map(\.at), [start.addingTimeInterval(-45 * 60), start.addingTimeInterval(-5 * 60)])
    }

    func testEmptyLeadListMeansDefault() {
        XCTAssertEqual(event([]).leadTimes(default: 20), [20])
    }

    func testNormalizeLeadTimes() {
        XCTAssertEqual(normalizeLeadTimes([5, 45, 5]), [45, 5])
        XCTAssertNil(normalizeLeadTimes([]))
        XCTAssertNil(normalizeLeadTimes(nil))
    }

    func testLeadText() {
        XCTAssertEqual(leadText(0), "at start time")
        XCTAssertEqual(leadText(20), "20 min before")
    }

    func testSelectedEventRoundTripsThroughJSON() throws {
        let original = event([30, 1])
        let decoded = try JSONDecoder().decode(SelectedEvent.self, from: JSONEncoder().encode(original))
        XCTAssertEqual(decoded, original)
    }
}
