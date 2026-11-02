import CalendarAlarmCore
import SwiftUI

/// Temporary home screen: connect Google Calendar (the events list arrives with #53).
struct ContentView: View {
    @EnvironmentObject private var auth: AuthService

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
