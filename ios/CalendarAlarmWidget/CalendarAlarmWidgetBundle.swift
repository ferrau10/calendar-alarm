import ActivityKit
import AlarmKit
import SwiftUI
import WidgetKit

@main
struct CalendarAlarmWidgetBundle: WidgetBundle {
    var body: some Widget {
        AlarmLiveActivity()
    }
}

/// Lock Screen / Dynamic Island while a meeting alarm is snoozed (AlarmKit's countdown presentation).
struct AlarmLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<MeetingAlarmMetadata>.self) { context in
            HStack(spacing: 12) {
                Image(systemName: "alarm.fill")
                    .font(.title2)
                    .foregroundStyle(context.attributes.tintColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.presentation.alert.title)
                        .font(.headline)
                        .lineLimit(1)
                    Text("Snoozed · rings again in 2 min")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "alarm.fill").foregroundStyle(context.attributes.tintColor)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.attributes.presentation.alert.title).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Snoozed · rings again in 2 min").font(.caption).foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "alarm.fill").foregroundStyle(context.attributes.tintColor)
            } compactTrailing: {
                Image(systemName: "zzz")
            } minimal: {
                Image(systemName: "alarm.fill").foregroundStyle(context.attributes.tintColor)
            }
        }
    }
}
