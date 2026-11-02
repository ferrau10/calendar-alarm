import XCTest
@testable import CalendarAlarmCore

final class AlarmSyncerTests: XCTestCase {
    private let now = ISO8601DateFormatter().date(from: "2026-11-02T08:00:00Z")!
    private var calendar: FakeCalendar!
    private var store: InMemoryKeyValueStore!
    private var selections: SelectionStore!
    private var settings: SettingsStore!
    private var scheduler: FakeScheduler!
    private var registry: ScheduledAlarmRegistry!
    private var syncer: AlarmSyncer!

    override func setUp() {
        calendar = FakeCalendar()
        store = InMemoryKeyValueStore()
        selections = SelectionStore(store: store)
        settings = SettingsStore(store: store)
        scheduler = FakeScheduler()
        registry = ScheduledAlarmRegistry(store: store)
        let fixed = now
        syncer = AlarmSyncer(calendar: calendar, selections: selections, settings: settings,
                             scheduler: scheduler, registry: registry, now: { fixed })
    }

    private func at(_ minutes: Int) -> Date { now.addingTimeInterval(TimeInterval(minutes * 60)) }

    @discardableResult
    private func event(_ id: String, startIn: Int, title: String? = nil, meet: String? = nil) -> CalendarEvent {
        let e = CalendarEvent(id: id, calendarId: "primary", title: title ?? "Meeting \(id)",
                              start: at(startIn), end: at(startIn + 30), allDay: false, meetLink: meet)
        calendar.events[id] = e
        return e
    }

    private func select(_ e: CalendarEvent, lead: [Int]? = nil) {
        selections.upsert(SelectedEvent(eventId: e.id, calendarId: e.calendarId, title: e.title, start: e.start, leadMinutes: lead))
    }

    func testSchedulesDefaultAndSeveralAlarms() async {
        select(event("a", startIn: 60))
        select(event("b", startIn: 120), lead: [45, 5])

        let result = await syncer.sync()

        XCTAssertEqual(result.scheduled, ["a@5", "b@45", "b@5"])
        XCTAssertEqual(scheduler.alarms["a@5"], .init(at: at(55), label: "Meeting a · in 5 min"))
        XCTAssertEqual(scheduler.alarms["b@45"]?.at, at(75))
        XCTAssertEqual(registry.current, ["a@5", "b@45", "b@5"])
    }

    func testMovedRenamedAndCancelledEvents() async {
        select(event("moved", startIn: 60))
        select(event("renamed", startIn: 90))
        select(event("gone", startIn: 120))
        await syncer.sync()
        event("moved", startIn: 150)
        event("renamed", startIn: 90, title: "Renamed")
        calendar.events["gone"] = nil

        let result = await syncer.sync()

        XCTAssertEqual(scheduler.alarms["moved@5"]?.at, at(145))
        XCTAssertEqual(scheduler.alarms["renamed@5"]?.label, "Renamed · in 5 min")
        XCTAssertNil(scheduler.alarms["gone@5"])
        XCTAssertEqual(result.removedSelections, ["gone"])
        XCTAssertNil(selections.get("gone"))
    }

    func testPastEventsArePrunedAndPassedAlarmsSkipped() async {
        select(event("past", startIn: -10))
        select(event("soon", startIn: 3))
        await syncer.sync()
        XCTAssertNil(selections.get("past"))
        XCTAssertNotNil(selections.get("soon"))
        XCTAssertTrue(scheduler.alarms.isEmpty)
    }

    func testRemovingALeadTimeCancelsThatAlarm() async {
        let e = event("a", startIn: 60)
        select(e, lead: [45, 5])
        await syncer.sync()
        select(e, lead: [5])

        let result = await syncer.sync(refreshFromCalendar: false)

        XCTAssertEqual(Set(scheduler.alarms.keys), ["a@5"])
        XCTAssertEqual(result.cancelled, ["a@45"])
    }

    func testOfflineKeepsAlarmsAndLocalSyncSkipsNetwork() async {
        select(event("a", startIn: 60))
        await syncer.sync()
        calendar.offline = true
        let fetchesBefore = calendar.fetches

        let offline = await syncer.sync()
        XCTAssertTrue(offline.offline)
        XCTAssertNotNil(scheduler.alarms["a@5"])

        let local = await syncer.sync(refreshFromCalendar: false)
        XCTAssertFalse(local.offline)
        XCTAssertEqual(calendar.fetches, fetchesBefore + 1)
    }

    func testAutoSelectAddsMatchingButNotDismissed() async {
        event("call", startIn: 120, meet: "https://meet.google.com/x")
        event("dismissed", startIn: 130, meet: "https://meet.google.com/y")
        event("plain", startIn: 140)
        settings.autoSelectVideoCalls = true
        settings.addDismissed("dismissed")

        await syncer.sync()

        XCTAssertEqual(Set(selections.all().map(\.eventId)), ["call"])
        XCTAssertNotNil(scheduler.alarms["call@5"])
    }
}
