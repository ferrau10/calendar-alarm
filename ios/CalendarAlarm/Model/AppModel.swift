import BackgroundTasks
import CalendarAlarmCore
import Foundation
import SwiftUI

/// App state shared by the screens (the iOS counterpart of the Android view models + wiring).
@MainActor
final class AppModel: ObservableObject {
    enum ListState: Equatable {
        case loading, notConnected, error(String), content([DaySection])
    }

    static let refreshTaskId = "io.github.ferrau10.calendaralarm.refresh"

    let auth: AuthService
    let alarms: AlarmKitScheduler
    private let selections: SelectionStore
    let settings: SettingsStore
    private let calendar: MultiAccountCalendarRepository
    private let syncer: AlarmSyncer
    private let mapper = EventsMapper()

    @Published private(set) var list: ListState = .loading
    @Published private(set) var calendars: [CalendarInfo] = []
    @Published private(set) var alarmsAuthorized = true
    @Published var defaultLead: Int { didSet { settings.defaultLead = defaultLead; resyncLocal() } }
    @Published var autoVideo: Bool { didSet { settings.autoSelectVideoCalls = autoVideo; resync() } }
    @Published var autoAccepted: Bool { didSet { settings.autoSelectAcceptedMeetings = autoAccepted; resync() } }

    private var events: [CalendarEvent] = []

    init(auth: AuthService) {
        let store = UserDefaultsStore()
        let alarms = AlarmKitScheduler()
        let selections = SelectionStore(store: store)
        let settings = SettingsStore(store: store)
        let clients = ClientCache(auth: auth)
        let calendar = MultiAccountCalendarRepository(
            accounts: { await auth.accounts },
            apiFor: { clients.client(for: $0) }
        )
        self.auth = auth
        self.alarms = alarms
        self.selections = selections
        self.settings = settings
        self.calendar = calendar
        self.syncer = AlarmSyncer(calendar: calendar, selections: selections, settings: settings,
                                  scheduler: alarms, registry: ScheduledAlarmRegistry(store: store))
        self.defaultLead = settings.defaultLead
        self.autoVideo = settings.autoSelectVideoCalls
        self.autoAccepted = settings.autoSelectAcceptedMeetings
    }

    var enabledCalendarIds: Set<String> {
        let chosen = settings.enabledCalendarIds
        return chosen.isEmpty ? Set(calendars.filter(\.primary).map(\.id)) : chosen
    }

    // MARK: Loading

    /// Loads calendars and the next 7 days of events, then refreshes alarms from the calendar.
    func refresh() async {
        guard !auth.accounts.isEmpty else { list = .notConnected; return }
        if case .content = list {} else { list = .loading }
        do {
            calendars = try await calendar.calendars()
            let ids = enabledCalendarIds.isEmpty ? ["primary"] : enabledCalendarIds
            let now = Date()
            events = try await calendar.upcomingEvents(calendarIds: ids, from: now, to: now.addingTimeInterval(7 * 24 * 3600))
            rebuild()
        } catch is NotAuthorizedError {
            list = .notConnected
        } catch {
            list = .error(error.localizedDescription)
        }
        alarmsAuthorized = await alarms.requestAuthorizationIfNeeded()
        await syncer.sync(refreshFromCalendar: true)
        rebuild()
    }

    private func rebuild() {
        guard !auth.accounts.isEmpty else { list = .notConnected; return }
        if case .error = list, events.isEmpty { return }
        let colors = Dictionary(uniqueKeysWithValues: calendars.map { ($0.id, $0.colorHex) })
        list = .content(mapper.sections(events: events, selections: selections.all(), calendarColors: colors,
                                        defaultLead: settings.defaultLead, now: Date()))
    }

    // MARK: Editing

    func toggle(_ row: EventRow) {
        if row.selected {
            selections.remove(row.id)
            settings.addDismissed(row.id) // auto-select rules won't add it back
        } else if !row.allDay {
            selections.upsert(SelectedEvent(eventId: row.id, calendarId: row.calendarId, title: row.title,
                                            start: row.start, leadMinutes: nil,
                                            account: row.account.isEmpty ? nil : row.account))
        }
        rebuild()
        resyncLocal()
    }

    /// Sets this meeting's alarm times (nil/empty = default); also turns its alarm on.
    func setLeadMinutes(_ row: EventRow, _ minutes: [Int]?) {
        guard !row.allDay else { return }
        selections.upsert(SelectedEvent(eventId: row.id, calendarId: row.calendarId, title: row.title,
                                        start: row.start, leadMinutes: normalizeLeadTimes(minutes),
                                        account: row.account.isEmpty ? nil : row.account))
        rebuild()
        resyncLocal()
    }

    func toggleCalendar(_ id: String) {
        var ids = enabledCalendarIds
        if ids.contains(id) { ids.remove(id) } else { ids.insert(id) }
        settings.enabledCalendarIds = ids
        Task { await refresh() }
    }

    /// Removes one account with its meetings (alarms are cancelled by the sync) and calendar choices.
    func removeAccount(_ email: String) async {
        let wasFirst = auth.accounts.first == email
        for selection in selections.all() where selection.account == email || (selection.account == nil && wasFirst) {
            selections.remove(selection.eventId)
        }
        let own = Set(calendars.filter { $0.account == email }.map(\.id))
        if !settings.enabledCalendarIds.isDisjoint(with: own) {
            settings.enabledCalendarIds = settings.enabledCalendarIds.subtracting(own)
        }
        await auth.remove(email)
        events.removeAll { $0.account == email }
        await syncer.sync(refreshFromCalendar: false)
        await refresh()
    }

    func connect() async {
        await auth.connect()
        await refresh()
    }

    // MARK: Sync

    private func resyncLocal() {
        Task {
            await syncer.sync(refreshFromCalendar: false)
            rebuild()
        }
    }

    private func resync() {
        Task {
            await syncer.sync(refreshFromCalendar: true)
            rebuild()
        }
    }

    /// Background app refresh: follow moved/cancelled meetings while the app isn't open.
    nonisolated func backgroundSync() async {
        await syncer.sync(refreshFromCalendar: true)
        await scheduleBackgroundRefresh()
    }

    func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshTaskId)
        request.earliestBeginDate = Date().addingTimeInterval(30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

/// One calendar client per account, each with that account's tokens.
private final class ClientCache: @unchecked Sendable {
    private let auth: AuthService
    private var clients: [String: GoogleCalendarClient] = [:]
    private let lock = NSLock()

    init(auth: AuthService) { self.auth = auth }

    func client(for account: String) -> GoogleCalendarClient {
        lock.withLock {
            if let existing = clients[account] { return existing }
            let auth = self.auth
            let client = GoogleCalendarClient(tokens: { _ in try await auth.accessToken(for: account) })
            clients[account] = client
            return client
        }
    }
}

