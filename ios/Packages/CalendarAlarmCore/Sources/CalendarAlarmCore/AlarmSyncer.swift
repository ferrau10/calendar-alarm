import Foundation

public struct SyncResult: Equatable, Sendable {
    public var scheduled: Set<String> = []
    public var cancelled: Set<String> = []
    public var removedSelections: Set<String> = []
    public var updatedSelections: Set<String> = []
    /// The calendar couldn't be reached; affected alarms were kept as they were.
    public var offline = false
}

/// Which events the optional auto-select rules pick.
public enum AutoSelectRules {
    public static func matches(_ event: CalendarEvent, videoCalls: Bool, acceptedMeetings: Bool) -> Bool {
        guard !event.allDay else { return false }
        return (videoCalls && event.meetLink != nil) || (acceptedMeetings && event.hasOtherAttendees && event.acceptedByMe)
    }
}

/// Turns selected events into scheduled alarms and keeps them correct when meetings move or are cancelled
/// (port of the Android `AlarmSyncer`). Network failures never drop an alarm.
public actor AlarmSyncer {
    public static let autoSelectWindow: TimeInterval = 7 * 24 * 3600

    private let calendar: CalendarRepository
    private let selections: SelectionStore
    private let settings: SettingsStore
    private let scheduler: AlarmScheduler
    private let registry: ScheduledAlarmRegistry
    private let now: @Sendable () -> Date

    public init(
        calendar: CalendarRepository, selections: SelectionStore, settings: SettingsStore,
        scheduler: AlarmScheduler, registry: ScheduledAlarmRegistry, now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.calendar = calendar
        self.selections = selections
        self.settings = settings
        self.scheduler = scheduler
        self.registry = registry
        self.now = now
    }

    /// - Parameter refreshFromCalendar: fetch the latest version of each selected event first. Pass false for
    ///   purely local changes (selection or alarm times edited) to reschedule without network.
    @discardableResult
    public func sync(refreshFromCalendar: Bool = true) async -> SyncResult {
        let now = now()
        let defaultLead = settings.defaultLead
        var result = SyncResult()

        if refreshFromCalendar {
            let reachable = await autoSelect(now: now)
            if !reachable { result.offline = true }
        }

        var active: [SelectedEvent] = []
        for selection in selections.all() {
            var current = selection
            if refreshFromCalendar {
                do {
                    guard let event = try await calendar.event(account: selection.account, calendarId: selection.calendarId,
                                                               eventId: selection.eventId) else {
                        selections.remove(selection.eventId)
                        result.removedSelections.insert(selection.eventId)
                        continue
                    }
                    let missingAccount = selection.account == nil && !event.account.isEmpty
                    if event.start != selection.start || event.title != selection.title || missingAccount {
                        current.start = event.start
                        current.title = event.title
                        if missingAccount { current.account = event.account }
                        selections.upsert(current)
                        result.updatedSelections.insert(selection.eventId)
                    }
                } catch {
                    result.offline = true
                }
            }
            if current.start <= now {
                selections.remove(current.eventId)
                result.removedSelections.insert(current.eventId)
                continue
            }
            active.append(current)
        }

        for selection in active {
            for alarm in selection.alarms(default: defaultLead) {
                if alarm.at > now {
                    do {
                        try await scheduler.schedule(key: alarm.key, at: alarm.at,
                                                     label: alarmLabel(title: selection.title, leadMinutes: alarm.leadMinutes))
                        result.scheduled.insert(alarm.key)
                    } catch {
                        // Not authorized or system refused; leave it out so the next sync retries.
                    }
                } else {
                    await scheduler.cancel(key: alarm.key)
                }
            }
        }

        result.cancelled = registry.current.subtracting(result.scheduled)
        for key in result.cancelled { await scheduler.cancel(key: key) }
        registry.replace(result.scheduled)
        return result
    }

    /// Adds upcoming events matching the enabled rules; returns false if the calendar couldn't be reached.
    private func autoSelect(now: Date) async -> Bool {
        let video = settings.autoSelectVideoCalls
        let accepted = settings.autoSelectAcceptedMeetings
        guard video || accepted else { return true }
        let dismissed = settings.dismissedEventIds
        let existing = Set(selections.all().map(\.eventId))
        do {
            var ids = settings.enabledCalendarIds
            if ids.isEmpty { ids = Set(try await calendar.calendars().filter(\.primary).map(\.id)) }
            let events = try await calendar.upcomingEvents(calendarIds: ids, from: now, to: now.addingTimeInterval(Self.autoSelectWindow))
            for event in events where event.start > now && !existing.contains(event.id) && !dismissed.contains(event.id)
                && AutoSelectRules.matches(event, videoCalls: video, acceptedMeetings: accepted) {
                selections.upsert(SelectedEvent(eventId: event.id, calendarId: event.calendarId, title: event.title,
                                                start: event.start, leadMinutes: nil,
                                                account: event.account.isEmpty ? nil : event.account))
            }
            return true
        } catch {
            return false
        }
    }
}
