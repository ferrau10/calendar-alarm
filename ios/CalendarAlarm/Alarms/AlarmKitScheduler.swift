import AlarmKit
import CalendarAlarmCore
import Foundation
import SwiftUI

/// Data attached to each system alarm.
struct MeetingAlarmMetadata: AlarmMetadata {
    let key: String
}

/// `AlarmScheduler` backed by AlarmKit: real alarms that ring through Silent mode and Focus and show on the
/// Lock Screen, with the meeting's name.
final class AlarmKitScheduler: AlarmScheduler {
    private var manager: AlarmManager { AlarmManager.shared }

    /// Asks for permission if it hasn't been decided yet; returns whether alarms can be scheduled.
    @discardableResult
    func requestAuthorizationIfNeeded() async -> Bool {
        switch manager.authorizationState {
        case .authorized: return true
        case .denied: return false
        default: return (try? await manager.requestAuthorization()) == .authorized
        }
    }

    var isAuthorized: Bool { manager.authorizationState == .authorized }

    func schedule(key: String, at date: Date, label: String) async throws {
        guard date > Date() else { return }
        let id = alarmUUID(forKey: key)
        try? manager.cancel(id: id) // replace an existing alarm for this key

        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: label),
            stopButton: AlarmButton(text: "Stop", textColor: .white, systemImageName: "stop.circle")
        )
        let attributes = AlarmAttributes<MeetingAlarmMetadata>(
            presentation: AlarmPresentation(alert: alert),
            metadata: MeetingAlarmMetadata(key: key),
            tintColor: Color(red: 0.10, green: 0.45, blue: 0.91)
        )
        let configuration = AlarmManager.AlarmConfiguration<MeetingAlarmMetadata>.alarm(
            schedule: .fixed(date),
            attributes: attributes,
            stopIntent: nil,
            secondaryIntent: nil,
            sound: .default
        )
        _ = try await manager.schedule(id: id, configuration: configuration)
    }

    func cancel(key: String) async {
        try? manager.cancel(id: alarmUUID(forKey: key))
    }
}
