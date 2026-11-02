import CalendarAlarmCore
import SwiftUI

/// Placeholder until the events list (#53) lands.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "alarm.fill")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Calendar Alarm")
                .font(.largeTitle.bold())
            Text("Alarms \(leadText(defaultLeadMinutes)) your meetings")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
