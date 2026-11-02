import Foundation

/// Minimal key-value persistence (UserDefaults in the app, in-memory in tests).
public protocol KeyValueStore: AnyObject, Sendable {
    func data(forKey key: String) -> Data?
    func set(_ data: Data?, forKey key: String)
}

public final class InMemoryKeyValueStore: KeyValueStore, @unchecked Sendable {
    private var values: [String: Data] = [:]
    private let lock = NSLock()
    public init() {}
    public func data(forKey key: String) -> Data? { lock.withLock { values[key] } }
    public func set(_ data: Data?, forKey key: String) { lock.withLock { values[key] = data } }
}

/// A Codable value persisted in a KeyValueStore.
final class Persisted<Value: Codable>: @unchecked Sendable {
    private let store: KeyValueStore
    private let key: String
    private let defaultValue: Value
    private let lock = NSLock()

    init(_ store: KeyValueStore, key: String, default defaultValue: Value) {
        self.store = store
        self.key = key
        self.defaultValue = defaultValue
    }

    var value: Value {
        lock.withLock {
            store.data(forKey: key).flatMap { try? JSONDecoder().decode(Value.self, from: $0) } ?? defaultValue
        }
    }

    func update(_ change: (inout Value) -> Void) {
        lock.withLock {
            var current = store.data(forKey: key).flatMap { try? JSONDecoder().decode(Value.self, from: $0) } ?? defaultValue
            change(&current)
            store.set(try? JSONEncoder().encode(current), forKey: key)
        }
    }
}

/// Events the user selected for alarms.
public final class SelectionStore: Sendable {
    private let items: Persisted<[String: SelectedEvent]>

    public init(store: KeyValueStore) {
        items = Persisted(store, key: "selections", default: [:])
    }

    public func all() -> [SelectedEvent] { items.value.values.sorted { ($0.start, $0.eventId) < ($1.start, $1.eventId) } }
    public func get(_ eventId: String) -> SelectedEvent? { items.value[eventId] }
    public func upsert(_ event: SelectedEvent) { items.update { $0[event.eventId] = event } }
    public func remove(_ eventId: String) { items.update { $0[eventId] = nil } }
}

/// User preferences (mirrors the Android `SettingsStore`).
public final class SettingsStore: Sendable {
    public static let maxLeadMinutes = 120
    public static let maxDismissed = 500

    private let lead: Persisted<Int>
    private let calendars: Persisted<Set<String>>
    private let autoVideo: Persisted<Bool>
    private let autoAccepted: Persisted<Bool>
    private let dismissed: Persisted<[String]>

    public init(store: KeyValueStore) {
        lead = Persisted(store, key: "defaultLeadMinutes", default: defaultLeadMinutes)
        calendars = Persisted(store, key: "enabledCalendarIds", default: [])
        autoVideo = Persisted(store, key: "autoSelectVideoCalls", default: false)
        autoAccepted = Persisted(store, key: "autoSelectAcceptedMeetings", default: false)
        dismissed = Persisted(store, key: "dismissedEventIds", default: [])
    }

    public var defaultLead: Int {
        get { min(max(lead.value, 0), Self.maxLeadMinutes) }
        set { lead.update { $0 = min(max(newValue, 0), Self.maxLeadMinutes) } }
    }

    /// Calendars to show; empty means "primary calendar only".
    public var enabledCalendarIds: Set<String> {
        get { calendars.value }
        set { calendars.update { $0 = newValue } }
    }

    public var autoSelectVideoCalls: Bool {
        get { autoVideo.value }
        set { autoVideo.update { $0 = newValue } }
    }

    public var autoSelectAcceptedMeetings: Bool {
        get { autoAccepted.value }
        set { autoAccepted.update { $0 = newValue } }
    }

    /// Events the user turned off; auto-select never adds them back.
    public var dismissedEventIds: Set<String> { Set(dismissed.value) }

    public func addDismissed(_ eventId: String) {
        dismissed.update { list in
            list.removeAll { $0 == eventId }
            list.append(eventId)
            if list.count > Self.maxDismissed { list.removeFirst(list.count - Self.maxDismissed) }
        }
    }
}

/// Remembers which alarm keys are scheduled, so alarms for removed selections can be cancelled.
public final class ScheduledAlarmRegistry: Sendable {
    private let keys: Persisted<Set<String>>
    public init(store: KeyValueStore) { keys = Persisted(store, key: "scheduledAlarmKeys", default: []) }
    public var current: Set<String> { keys.value }
    public func replace(_ newKeys: Set<String>) { keys.update { $0 = newKeys } }
}
