import XCTest
@testable import CalendarAlarmCore

final class StorageTests: XCTestCase {
    func testSelectionsPersistAcrossInstances() {
        let kv = InMemoryKeyValueStore()
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        SelectionStore(store: kv).upsert(SelectedEvent(eventId: "a", calendarId: "primary", title: "A", start: start, leadMinutes: [45, 5]))
        let reopened = SelectionStore(store: kv)
        XCTAssertEqual(reopened.all().first?.leadMinutes, [45, 5])
        reopened.remove("a")
        XCTAssertTrue(SelectionStore(store: kv).all().isEmpty)
    }

    func testSettingsDefaultsClampAndDismissedLimit() {
        let settings = SettingsStore(store: InMemoryKeyValueStore())
        XCTAssertEqual(settings.defaultLead, 5)
        settings.defaultLead = 500
        XCTAssertEqual(settings.defaultLead, 120)
        settings.defaultLead = -1
        XCTAssertEqual(settings.defaultLead, 0)
        for i in 0..<510 { settings.addDismissed("e\(i)") }
        XCTAssertEqual(settings.dismissedEventIds.count, SettingsStore.maxDismissed)
        XCTAssertTrue(settings.dismissedEventIds.contains("e509"))
        XCTAssertFalse(settings.dismissedEventIds.contains("e0"))
    }

    func testParseLeadMinutes() {
        XCTAssertEqual(parseLeadMinutes(" 17 "), .success(17))
        XCTAssertEqual(parseLeadMinutes(""), .failure(.empty))
        XCTAssertEqual(parseLeadMinutes("7.5"), .failure(.notANumber))
        XCTAssertEqual(parseLeadMinutes("121"), .failure(.outOfRange))
    }
}

final class EventsMapperTests: XCTestCase {
    private let utc = TimeZone(identifier: "UTC")!
    private let now = ISO8601DateFormatter().date(from: "2026-11-02T08:00:00Z")!

    private func event(_ id: String, _ start: String, _ end: String, allDay: Bool = false) -> CalendarEvent {
        let f = ISO8601DateFormatter()
        return CalendarEvent(id: id, calendarId: "primary", title: "Event \(id)", start: f.date(from: start)!,
                             end: f.date(from: end)!, allDay: allDay)
    }

    func testGroupsByDayAndDescribesAlarms() {
        let mapper = EventsMapper(timeZone: utc, locale: Locale(identifier: "en_GB"))
        let a = event("a", "2026-11-02T09:00:00Z", "2026-11-02T09:30:00Z")
        let b = event("b", "2026-11-03T10:00:00Z", "2026-11-03T10:30:00Z")
        let holiday = event("h", "2026-11-02T00:00:00Z", "2026-11-03T00:00:00Z", allDay: true)
        let selected = [SelectedEvent(eventId: "a", calendarId: "primary", title: "Event a", start: a.start, leadMinutes: [5, 45])]

        let sections = mapper.sections(events: [b, a, holiday], selections: selected, calendarColors: ["primary": "#4285f4"],
                                       defaultLead: 5, now: now)

        XCTAssertEqual(sections.map(\.label), ["Today", "Tomorrow"])
        XCTAssertEqual(sections[0].rows.map(\.id), ["h", "a"])
        XCTAssertEqual(sections[0].rows[0].timeText, "All day")
        XCTAssertEqual(sections[0].rows[1].alarmText, "Alarms at 08:15 and 08:55 (45 and 5 min before)")
        XCTAssertEqual(sections[0].rows[1].colorHex, "#4285f4")
        XCTAssertNil(sections[1].rows[0].alarmText)
    }

    func testLeadsText() {
        XCTAssertEqual(EventsMapper.leadsText([5]), "5 min before")
        XCTAssertEqual(EventsMapper.leadsText([45, 5]), "45 and 5 min before")
        XCTAssertEqual(EventsMapper.leadsText([10, 0]), "10 min before and at start time")
    }
}
