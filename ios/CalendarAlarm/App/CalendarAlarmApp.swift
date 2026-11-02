import SwiftUI

@main
struct CalendarAlarmApp: App {
    @StateObject private var auth = AuthService()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(auth)
                .task { await auth.restore() }
                .onOpenURL { _ = auth.handle(url: $0) }
        }
    }
}
