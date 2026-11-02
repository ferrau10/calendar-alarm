import Foundation

/// Schedules the device alarms (AlarmKit on iOS); mirrors the Android `AlarmScheduler`.
public protocol AlarmScheduler: Sendable {
    /// Schedules (or replaces) the alarm identified by `key`. Times in the past are ignored.
    func schedule(key: String, at date: Date, label: String) async throws
    func cancel(key: String) async
}

/// Deterministic UUID for an alarm key, so the same key always maps to the same system alarm.
public func alarmUUID(forKey key: String) -> UUID {
    let bytes = Array(key.utf8)
    let a = fnv1a64(bytes, seed: 0xcbf2_9ce4_8422_2325)
    let b = fnv1a64(bytes, seed: 0x8422_2325_cbf2_9ce4)
    var raw = [UInt8](repeating: 0, count: 16)
    for i in 0..<8 {
        raw[i] = UInt8(truncatingIfNeeded: a >> (UInt64(i) * 8))
        raw[8 + i] = UInt8(truncatingIfNeeded: b >> (UInt64(i) * 8))
    }
    raw[6] = (raw[6] & 0x0F) | 0x50 // version 5-style (name-based)
    raw[8] = (raw[8] & 0x3F) | 0x80 // RFC 4122 variant
    return UUID(uuid: (raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7],
                       raw[8], raw[9], raw[10], raw[11], raw[12], raw[13], raw[14], raw[15]))
}

private func fnv1a64(_ bytes: [UInt8], seed: UInt64) -> UInt64 {
    var hash = seed
    for byte in bytes {
        hash ^= UInt64(byte)
        hash = hash &* 0x0000_0100_0000_01B3
    }
    return hash
}

/// "Standup · in 5 min" (or just the title when the alarm rings at the start time).
public func alarmLabel(title: String, leadMinutes: Int) -> String {
    leadMinutes == 0 ? title : "\(title) · in \(leadMinutes) min"
}
