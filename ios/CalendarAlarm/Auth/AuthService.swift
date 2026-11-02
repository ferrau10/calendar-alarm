import CalendarAlarmCore
import GoogleSignIn
import SwiftUI
import UIKit

/// Google sign-in with the Calendar read-only scope (mirrors the Android `GoogleAuthRepository`).
@MainActor
final class AuthService: ObservableObject {
    static let calendarScope = "https://www.googleapis.com/auth/calendar.readonly"

    /// Email of the connected account, or nil.
    @Published private(set) var account: String?
    @Published private(set) var lastError: String?

    /// Restores the previous session silently (call on launch).
    func restore() async {
        guard GIDSignIn.sharedInstance.hasPreviousSignIn() else { return }
        do {
            let user = try await GIDSignIn.sharedInstance.restorePreviousSignIn()
            account = hasCalendarScope(user) ? user.profile?.email : nil
        } catch {
            account = nil
        }
    }

    /// Shows Google's sign-in and consent screens.
    func connect() async {
        lastError = nil
        guard let presenter = Self.topViewController() else { return }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter, hint: nil, additionalScopes: [Self.calendarScope]
            )
            guard hasCalendarScope(result.user) else {
                lastError = "Calendar access is needed to show your meetings."
                return
            }
            account = result.user.profile?.email
        } catch let error as GIDSignInError where error.code == .canceled {
            // User closed the sheet.
        } catch {
            lastError = error.localizedDescription
        }
    }

    /// Signs out and revokes the app's access.
    func disconnect() async {
        try? await GIDSignIn.sharedInstance.disconnect()
        GIDSignIn.sharedInstance.signOut()
        account = nil
    }

    /// A valid access token for the Calendar API. GoogleSignIn refreshes expired tokens itself; a token that's
    /// still rejected (access revoked) surfaces as NotAuthorizedError from the calendar client.
    func accessToken() async throws -> String {
        guard let user = GIDSignIn.sharedInstance.currentUser else { throw NotAuthorizedError() }
        return try await user.refreshTokensIfNeeded().accessToken.tokenString
    }

    /// Handles the redirect back from Google's sign-in page.
    func handle(url: URL) -> Bool { GIDSignIn.sharedInstance.handle(url) }

    private func hasCalendarScope(_ user: GIDGoogleUser) -> Bool {
        user.grantedScopes?.contains(Self.calendarScope) == true
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
