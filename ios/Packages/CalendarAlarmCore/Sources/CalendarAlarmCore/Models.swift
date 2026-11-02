import Foundation

/// Minutes before a meeting the alarm rings when the user didn't choose otherwise.
public let defaultLeadMinutes = 5

/// Alarm times offered in the pickers (custom values are allowed too).
public let leadTimeChoices = [1, 5, 20, 30]

/// A calendar in the connected Google account.
public struct CalendarInfo: Equatable, Hashable, Sendable, Identifiable {
    public let id: String
    public let name: String
    public let colorHex: String?
    public let primary: Bool

    public init(id: String, name: String, colorHex: String?, primary: Bool) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.primary = primary
    }
}

/// A single event occurrence (recurring events are expanded into occurrences).
public struct CalendarEvent: Equatable, Hashable, Sendable, Identifiable {
    public let id: String
    public let calendarId: String
    public let title: String
    public let start: Date
    public let end: Date
    public let allDay: Bool
    public let location: String?
    public let meetLink: String?
    /// Other people are invited (not just me).
    public let hasOtherAttendees: Bool
    /// I accepted, or it's my own event.
    public let acceptedByMe: Bool

    public init(
        id: String, calendarId: String, title: String, start: Date, end: Date, allDay: Bool,
        location: String? = nil, meetLink: String? = nil, hasOtherAttendees: Bool = false, acceptedByMe: Bool = false
    ) {
        self.id = id
        self.calendarId = calendarId
        self.title = title
        self.start = start
        self.end = end
        self.allDay = allDay
        self.location = location
        self.meetLink = meetLink
        self.hasOtherAttendees = hasOtherAttendees
        self.acceptedByMe = acceptedByMe
    }
}

/// One alarm of a selected event.
public struct EventAlarm: Equatable, Hashable, Sendable {
    public let key: String
    public let leadMinutes: Int
    public let at: Date
}

/// An event the user wants alarms for.
public struct SelectedEvent: Equatable, Hashable, Codable, Sendable {
    public let eventId: String
    public let calendarId: String
    public var title: String
    public var start: Date
    /// Minutes before `start` to ring, one alarm each (e.g. `[45, 5]`); `nil` = one alarm at the default.
    public var leadMinutes: [Int]?

    public init(eventId: String, calendarId: String, title: String, start: Date, leadMinutes: [Int]?) {
        self.eventId = eventId
        self.calendarId = calendarId
        self.title = title
        self.start = start
        self.leadMinutes = leadMinutes
    }

    /// Lead times in effect, largest first (= earliest alarm first), without duplicates.
    public func leadTimes(default fallback: Int) -> [Int] {
        let chosen = (leadMinutes?.isEmpty == false ? leadMinutes! : [fallback])
        return Array(Set(chosen)).sorted(by: >)
    }

    /// Every alarm for this event, earliest first.
    public func alarms(default fallback: Int) -> [EventAlarm] {
        leadTimes(default: fallback).map { lead in
            EventAlarm(key: alarmKey(eventId: eventId, leadMinutes: lead), leadMinutes: lead,
                       at: start.addingTimeInterval(TimeInterval(-lead * 60)))
        }
    }
}

/// Stable id of the alarm ringing `leadMinutes` before an event starts.
public func alarmKey(eventId: String, leadMinutes: Int) -> String { "\(eventId)@\(leadMinutes)" }

/// Largest first, no duplicates; empty means "use the default".
public func normalizeLeadTimes(_ minutes: [Int]?) -> [Int]? {
    guard let minutes, !minutes.isEmpty else { return nil }
    return Array(Set(minutes)).sorted(by: >)
}

/// "5 min before" / "at start time".
public func leadText(_ minutes: Int) -> String { minutes == 0 ? "at start time" : "\(minutes) min before" }
