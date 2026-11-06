import SwiftUI

@main
struct CalendarAlarmApp: App {
    @StateObject private var auth: AuthService
    @StateObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let auth = AuthService()
        _auth = StateObject(wrappedValue: auth)
        _model = StateObject(wrappedValue: AppModel(auth: auth))
    }

    var body: some Scene {
        WindowGroup {
            EventsView()
                .environmentObject(auth)
                .environmentObject(model)
                .task {
                    auth.restore()
                    await model.refresh()
                }
                .onOpenURL { _ = auth.handle(url: $0) }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: Task { await model.refresh() }
            case .background: model.scheduleBackgroundRefresh()
            default: break
            }
        }
        .backgroundTask(.appRefresh(AppModel.refreshTaskId)) {
            await model.backgroundSync()
        }
    }
}
