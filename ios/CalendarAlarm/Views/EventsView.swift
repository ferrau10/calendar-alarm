import CalendarAlarmCore
import SwiftUI

/// Upcoming events; tap the switch to get an alarm, "Change" to pick when it rings.
struct EventsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editing: EventRow?
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Upcoming events")
                .toolbar {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings")
                }
                .refreshable { await model.refresh() }
                .sheet(item: $editing) { row in
                    LeadTimeSheet(row: row, defaultLead: model.defaultLead) { minutes in
                        model.setLeadMinutes(row, minutes)
                    }
                }
                .sheet(isPresented: $showSettings) { SettingsView() }
        }
    }

    @ViewBuilder private var content: some View {
        switch model.list {
        case .loading:
            ProgressView("Loading your calendar…")
        case .notConnected:
            ContentUnavailableView {
                Label("Connect Google Calendar", systemImage: "calendar")
            } description: {
                Text("Pick the meetings you care about and your iPhone rings an alarm before each one.")
            } actions: {
                Button("Connect") { Task { await model.connect() } }.buttonStyle(.borderedProminent)
                if let error = model.auth.lastError { Text(error).foregroundStyle(.red) }
            }
        case .error(let message):
            ContentUnavailableView {
                Label("Couldn't load your calendar", systemImage: "exclamationmark.triangle")
            } description: {
                Text(message)
            } actions: {
                Button("Try again") { Task { await model.refresh() } }
            }
        case .content(let sections) where sections.isEmpty:
            ContentUnavailableView("No events in the next 7 days", systemImage: "calendar")
        case .content(let sections):
            List {
                if !model.alarmsAuthorized {
                    Section {
                        Label("Alarms are turned off for Calendar Alarm. Allow them in Settings → Apps → Calendar Alarm.",
                              systemImage: "alarm.waves.left.and.right")
                            .foregroundStyle(.red)
                    }
                }
                ForEach(sections) { section in
                    Section(section.label) {
                        ForEach(section.rows) { row in
                            EventRowView(row: row, onToggle: { model.toggle(row) }, onChange: { editing = row })
                        }
                    }
                }
            }
        }
    }
}

private struct EventRowView: View {
    let row: EventRow
    let onToggle: () -> Void
    let onChange: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle().fill(Color(hex: row.colorHex) ?? .gray).frame(width: 10, height: 10).padding(.top, 6)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title).font(.body)
                Text(row.timeText).font(.subheadline).foregroundStyle(.secondary)
                if let alarmText = row.alarmText {
                    Button(action: onChange) {
                        Text("\(alarmText) · Change").font(.footnote.weight(.medium))
                    }
                    .buttonStyle(.borderless)
                }
            }
            Spacer()
            Toggle("Alarm", isOn: Binding(get: { row.selected }, set: { _ in onToggle() }))
                .labelsHidden()
                .disabled(row.allDay)
        }
        .contextMenu {
            if !row.allDay { Button("Change alarm time", systemImage: "alarm", action: onChange) }
        }
    }
}

/// Default, or several alarm times for one meeting (presets + custom).
struct LeadTimeSheet: View {
    let row: EventRow
    let defaultLead: Int
    let onSave: ([Int]?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var useDefault: Bool
    @State private var chosen: Set<Int>
    @State private var customText = ""

    init(row: EventRow, defaultLead: Int, onSave: @escaping ([Int]?) -> Void) {
        self.row = row
        self.defaultLead = defaultLead
        self.onSave = onSave
        _useDefault = State(initialValue: row.customLeadMinutes == nil)
        _chosen = State(initialValue: Set(row.customLeadMinutes ?? []))
    }

    private var offered: [Int] { Array(Set(leadTimeChoices).union(chosen)).sorted(by: >) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Alarm", selection: $useDefault) {
                        Text("Default (\(leadText(defaultLead)))").tag(true)
                        Text("Choose times").tag(false)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                if !useDefault {
                    Section("One alarm for each") {
                        ForEach(offered, id: \.self) { minutes in
                            Button {
                                if chosen.contains(minutes) { chosen.remove(minutes) } else { chosen.insert(minutes) }
                            } label: {
                                HStack {
                                    Text(leadText(minutes)).foregroundStyle(.primary)
                                    Spacer()
                                    if chosen.contains(minutes) { Image(systemName: "checkmark") }
                                }
                            }
                        }
                        HStack {
                            TextField("Custom minutes", text: $customText).keyboardType(.numberPad)
                            Button("Add") {
                                if case .success(let minutes) = parseLeadMinutes(customText) {
                                    chosen.insert(minutes)
                                    customText = ""
                                }
                            }
                            .disabled({ if case .success = parseLeadMinutes(customText) { return false }; return true }())
                        }
                        if !customText.isEmpty, case .failure(let error) = parseLeadMinutes(customText) {
                            Text(error.message).font(.footnote).foregroundStyle(.red)
                        }
                    }
                }
            }
            .navigationTitle(row.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Set alarms") {
                        onSave(useDefault ? nil : Array(chosen))
                        dismiss()
                    }
                    .disabled(!useDefault && chosen.isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

extension Color {
    /// "#RRGGBB" → Color.
    init?(hex: String?) {
        guard var s = hex?.trimmingCharacters(in: .whitespaces), s.hasPrefix("#"), s.count == 7 else { return nil }
        s.removeFirst()
        guard let v = UInt32(s, radix: 16) else { return nil }
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}
