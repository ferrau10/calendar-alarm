import Foundation
@testable import CalendarAlarmCore

struct Offline: Error {}

final class FakeCalendar: CalendarRepository, @unchecked Sendable {
    var events: [String: CalendarEvent] = [:]
    var calendarList = [CalendarInfo(id: "primary", name: "Me", colorHex: "#4285f4", primary: true)]
    var offline = false
    var fetches = 0

    func calendars() async throws -> [CalendarInfo] {
        if offline { throw Offline() }
        return calendarList
    }

    func upcomingEvents(calendarIds: Set<String>, from: Date, to: Date) async throws -> [CalendarEvent] {
        if offline { throw Offline() }
        return events.values.filter { calendarIds.contains($0.calendarId) && $0.start < to && $0.end > from }
            .sorted { $0.start < $1.start }
    }

    var lastEventAccount: String?

    func event(account: String?, calendarId: String, eventId: String) async throws -> CalendarEvent? {
        fetches += 1
        lastEventAccount = account
        if offline { throw Offline() }
        return events[eventId]
    }
}

final class FakeScheduler: AlarmScheduler, @unchecked Sendable {
    struct Alarm: Equatable { let at: Date; let label: String }
    var alarms: [String: Alarm] = [:]
    func schedule(key: String, at date: Date, label: String) async throws { alarms[key] = Alarm(at: date, label: label) }
    func cancel(key: String) async { alarms[key] = nil }
}
