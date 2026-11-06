import Foundation

public struct EventRow: Equatable, Identifiable, Sendable {
    public let id: String
    public let calendarId: String
    public let title: String
    public let start: Date
    /// "09:00 – 09:30" or "All day".
    public let timeText: String
    public let colorHex: String?
    public let allDay: Bool
    public let selected: Bool
    /// "Alarm at 08:55 (5 min before)" / "Alarms at 08:15 and 08:55 (45 and 5 min before)".
    public let alarmText: String?
    /// Per-event lead times, or nil when using the default.
    public let customLeadMinutes: [Int]?
    /// Google account the event comes from.
    public let account: String
}

public struct DaySection: Equatable, Identifiable, Sendable {
    public var id: String { label }
    public let day: Date
    public let label: String
    public let rows: [EventRow]
}

/// Pure mapping from calendar data to the events list (port of the Android `EventsUiMapper`).
public struct EventsMapper: Sendable {
    private let calendar: Calendar
    private let locale: Locale

    public init(timeZone: TimeZone = .current, locale: Locale = .current) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        self.calendar = cal
        self.locale = locale
    }

    public func sections(events: [CalendarEvent], selections: [SelectedEvent], calendarColors: [String: String?],
                         defaultLead: Int, now: Date) -> [DaySection] {
        let selected = Dictionary(uniqueKeysWithValues: selections.map { ($0.eventId, $0) })
        let sorted = events.sorted { a, b in
            if a.allDay != b.allDay { return a.allDay }
            return (a.start, a.title) < (b.start, b.title)
        }
        let grouped = Dictionary(grouping: sorted) { calendar.startOfDay(for: $0.start) }
        return grouped.keys.sorted().map { day in
            DaySection(day: day, label: dayLabel(day, now: now),
                       rows: grouped[day]!.map { row($0, selected[$0.id], calendarColors, defaultLead) })
        }
    }

    public func dayLabel(_ day: Date, now: Date) -> String {
        if calendar.isDate(day, inSameDayAs: now) { return "Today" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now), calendar.isDate(day, inSameDayAs: tomorrow) {
            return "Tomorrow"
        }
        let f = DateFormatter()
        f.calendar = calendar
        f.timeZone = calendar.timeZone
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate("EEE d MMM")
        return f.string(from: day)
    }

    private func time(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = calendar.timeZone
        f.locale = locale
        f.timeStyle = .short
        f.dateStyle = .none
        return f.string(from: date)
    }

    private func row(_ event: CalendarEvent, _ selection: SelectedEvent?, _ colors: [String: String?], _ defaultLead: Int) -> EventRow {
        let alarms = selection?.alarms(default: defaultLead) ?? []
        var alarmText: String?
        if !alarms.isEmpty {
            let times = Self.joinWithAnd(alarms.map { time($0.at) })
            alarmText = (alarms.count == 1 ? "Alarm at " : "Alarms at ") + "\(times) (\(Self.leadsText(alarms.map(\.leadMinutes))))"
        }
        return EventRow(
            id: event.id, calendarId: event.calendarId, title: event.title, start: event.start,
            timeText: event.allDay ? "All day" : "\(time(event.start)) – \(time(event.end))",
            colorHex: colors[event.calendarId] ?? nil, allDay: event.allDay, selected: selection != nil,
            alarmText: alarmText, customLeadMinutes: selection?.leadMinutes, account: event.account
        )
    }

    /// "5 min before", "45 and 5 min before", "10 min before and at start time".
    public static func leadsText(_ minutes: [Int]) -> String {
        let nonZero = minutes.filter { $0 != 0 }
        var parts: [String] = []
        if !nonZero.isEmpty { parts.append("\(joinWithAnd(nonZero.map(String.init))) min before") }
        if minutes.contains(0) { parts.append("at start time") }
        return joinWithAnd(parts)
    }

    public static func joinWithAnd(_ items: [String]) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        default: return items.dropLast().joined(separator: ", ") + " and " + items.last!
        }
    }
}

/// Validates a typed custom alarm time (0–120 minutes).
public func parseLeadMinutes(_ text: String) -> Result<Int, LeadMinutesError> {
    let trimmed = text.trimmingCharacters(in: .whitespaces)
    guard !trimmed.isEmpty else { return .failure(.empty) }
    guard let value = Int(trimmed) else { return .failure(.notANumber) }
    guard (0...SettingsStore.maxLeadMinutes).contains(value) else { return .failure(.outOfRange) }
    return .success(value)
}

public enum LeadMinutesError: Error, Equatable {
    case empty, notANumber, outOfRange
    public var message: String {
        switch self {
        case .empty: return "Enter a number of minutes"
        case .notANumber: return "Use whole minutes, e.g. 15"
        case .outOfRange: return "Choose between 0 and \(SettingsStore.maxLeadMinutes) minutes"
        }
    }
}
