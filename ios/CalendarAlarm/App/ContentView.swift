import CalendarAlarmCore
import SwiftUI

/// Temporary home screen: connect Google Calendar (the events list arrives with #53).
struct ContentView: View {
    @EnvironmentObject private var auth: AuthService
    @State private var testStatus: String?
    private let alarms = AlarmKitScheduler()

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "alarm.fill")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Calendar Alarm")
                .font(.largeTitle.bold())
            if let account = auth.account {
                Text("Connected as \(account)")
                Button("Disconnect", role: .destructive) { Task { await auth.disconnect() } }
            } else {
                Text("Alarms \(leadText(defaultLeadMinutes)) your meetings")
                    .foregroundStyle(.secondary)
                Button("Connect Google Calendar") { Task { await auth.connect() } }
                    .buttonStyle(.borderedProminent)
            }
            Button("Test alarm in 10 seconds") {
                Task {
                    guard await alarms.requestAuthorizationIfNeeded() else {
                        testStatus = "Allow alarms for Calendar Alarm in Settings to use this."
                        return
                    }
                    do {
                        try await alarms.schedule(key: "test-alarm", at: Date().addingTimeInterval(10),
                                                  label: alarmLabel(title: "Test meeting", leadMinutes: 5))
                        testStatus = "Rings in 10 seconds — lock your phone."
                    } catch {
                        testStatus = error.localizedDescription
                    }
                }
            }
            if let testStatus { Text(testStatus).font(.footnote).foregroundStyle(.secondary) }
            if let error = auth.lastError {
                Text(error).foregroundStyle(.red).multilineTextAlignment(.center)
            }
        }
        .padding()
    }
}

#Preview {
    ContentView().environmentObject(AuthService())
}
