import XCTest
@testable import CalendarAlarmCore

final class MultiAccountCalendarRepositoryTests: XCTestCase {
    private let t0 = ISO8601DateFormatter().date(from: "2026-11-02T08:00:00Z")!

    final class API: AccountCalendarAPI, @unchecked Sendable {
        let calendarList: [CalendarInfo]
        let events: [CalendarEvent]
        var failing = false
        var asked: [Set<String>] = []
        init(_ calendars: [CalendarInfo], _ events: [CalendarEvent]) { calendarList = calendars; self.events = events }
        func calendars() async throws -> [CalendarInfo] { if failing { throw Offline() }; return calendarList }
        func upcomingEvents(calendarIds: Set<String>, from: Date, to: Date) async throws -> [CalendarEvent] {
            if failing { throw Offline() }
            asked.append(calendarIds)
            return events.filter { calendarIds.contains($0.calendarId) }
        }
        func event(calendarId: String, eventId: String) async throws -> CalendarEvent? { events.first { $0.id == eventId } }
    }

    private func event(_ id: String, _ cal: String, _ minutes: Int) -> CalendarEvent {
        CalendarEvent(id: id, calendarId: cal, title: id, start: t0.addingTimeInterval(TimeInterval(minutes * 60)),
                      end: t0.addingTimeInterval(TimeInterval(minutes * 60 + 1800)), allDay: false)
    }

    private var me: API!
    private var work: API!
    private var accounts = ["me@example.com", "work@example.com"]

    override func setUp() {
        me = API([CalendarInfo(id: "me@example.com", name: "Me", colorHex: nil, primary: true),
                  CalendarInfo(id: "shared", name: "Family", colorHex: nil, primary: false)],
                 [event("a", "me@example.com", 60), event("both", "me@example.com", 90), event("s", "shared", 120)])
        work = API([CalendarInfo(id: "work@example.com", name: "Work", colorHex: nil, primary: true),
                    CalendarInfo(id: "shared", name: "Family (work)", colorHex: nil, primary: false)],
                   [event("w", "work@example.com", 30), event("both", "work@example.com", 90)])
    }

    private func repo() -> MultiAccountCalendarRepository {
        let list = accounts, me = me!, work = work!
        return MultiAccountCalendarRepository(accounts: { list }, apiFor: { $0 == "me@example.com" ? me : work })
    }

    func testCalendarsTaggedAndDeduplicated() async throws {
        let calendars = try await repo().calendars()
        XCTAssertEqual(calendars.map(\.id), ["me@example.com", "shared", "work@example.com"])
        XCTAssertEqual(calendars.map(\.account), ["me@example.com", "me@example.com", "work@example.com"])
    }

    func testEventsFromOwningAccountMergedAndDeduplicated() async throws {
        let r = repo()
        _ = try await r.calendars()
        let events = try await r.upcomingEvents(calendarIds: ["me@example.com", "work@example.com", "shared"],
                                                from: t0, to: t0.addingTimeInterval(86400))
        XCTAssertEqual(events.map(\.id), ["w", "a", "both", "s"])
        XCTAssertEqual(events.first { $0.id == "w" }?.account, "work@example.com")
        XCTAssertEqual(me.asked, [["me@example.com", "shared"]])
        XCTAssertEqual(work.asked, [["work@example.com"]])
    }

    func testOneFailingAccountDoesNotHideTheOthers() async throws {
        work.failing = true
        let calendars = try await repo().calendars()
        XCTAssertEqual(calendars.map(\.id), ["me@example.com", "shared"])
    }

    func testAllAccountsFailingThrows() async {
        me.failing = true
        work.failing = true
        do {
            _ = try await repo().calendars()
            XCTFail("expected an error")
        } catch {}
    }

    func testEventReadThroughGivenAccountOrOwner() async throws {
        let r = repo()
        let viaWork = try await r.event(account: "work@example.com", calendarId: "work@example.com", eventId: "w")
        XCTAssertEqual(viaWork?.account, "work@example.com")
        _ = try await r.calendars()
        let viaOwner = try await r.event(account: nil, calendarId: "work@example.com", eventId: "w")
        XCTAssertEqual(viaOwner?.account, "work@example.com")
        let fallback = try await r.event(account: nil, calendarId: "unknown", eventId: "a")
        XCTAssertEqual(fallback?.account, "me@example.com")
    }

    func testNoAccountsMeansNotAuthorized() async {
        accounts = []
        do {
            _ = try await repo().calendars()
            XCTFail("expected NotAuthorizedError")
        } catch is NotAuthorizedError {
        } catch {
            XCTFail("unexpected \(error)")
        }
    }
}
