import CalendarAlarmCore
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var customDefault = ""
    @State private var testStatus: String?

    private var defaultChoices: [Int] { Array(Set(leadTimeChoices).union([model.defaultLead])).sorted() }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Default alarm", selection: $model.defaultLead) {
                        ForEach(defaultChoices, id: \.self) { Text(leadText($0)).tag($0) }
                    }
                    HStack {
                        TextField("Custom minutes", text: $customDefault).keyboardType(.numberPad)
                        Button("Set") {
                            if case .success(let minutes) = parseLeadMinutes(customDefault) {
                                model.defaultLead = minutes
                                customDefault = ""
                            }
                        }
                    }
                } header: {
                    Text("Alarm time")
                } footer: {
                    Text("To change a single meeting, tap “Change” under it in the list. A meeting can have several alarms.")
                }

                Section {
                    Toggle("Events with a video call link", isOn: $model.autoVideo)
                    Toggle("Meetings I accepted with other people", isOn: $model.autoAccepted)
                } header: {
                    Text("Automatic alarms")
                } footer: {
                    Text("Matching events in the next 7 days get an alarm automatically. Turn one off and it stays off.")
                }

                if !model.calendars.isEmpty {
                    Section("Calendars to show") {
                        ForEach(model.calendars) { cal in
                            Button { model.toggleCalendar(cal.id) } label: {
                                HStack {
                                    Circle().fill(Color(hex: cal.colorHex) ?? .gray).frame(width: 10, height: 10)
                                    Text(cal.name).foregroundStyle(.primary)
                                    Spacer()
                                    if model.enabledCalendarIds.contains(cal.id) { Image(systemName: "checkmark") }
                                }
                            }
                        }
                    }
                }

                Section("Alarms") {
                    Button("Test alarm in 10 seconds") {
                        Task {
                            guard await model.alarms.requestAuthorizationIfNeeded() else {
                                testStatus = "Allow alarms for Calendar Alarm in Settings first."
                                return
                            }
                            do {
                                try await model.alarms.schedule(key: "test-alarm", at: Date().addingTimeInterval(10),
                                                                label: alarmLabel(title: "Test meeting", leadMinutes: 5))
                                testStatus = "Rings in 10 seconds — lock your iPhone."
                            } catch {
                                testStatus = error.localizedDescription
                            }
                        }
                    }
                    if let testStatus { Text(testStatus).font(.footnote).foregroundStyle(.secondary) }
                }

                Section("Google account") {
                    if let account = model.auth.account {
                        Text("Connected as \(account)")
                        Button("Disconnect", role: .destructive) { Task { await model.disconnect() } }
                    } else {
                        Text("Not connected")
                    }
                }

                Section {
                    Link("Privacy policy", destination: URL(string: "https://ferrau10.github.io/calendar-alarm/privacy.html")!)
                }
            }
            .navigationTitle("Settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
