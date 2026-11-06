import AppAuth
import CalendarAlarmCore
import SwiftUI
import UIKit

/// Any number of Google accounts, each with its own OAuth state (AppAuth) stored in the Keychain.
/// Mirrors the Android `GoogleAuthRepository`.
@MainActor
final class AuthService: ObservableObject {
    static let calendarScope = "https://www.googleapis.com/auth/calendar.readonly"
    private static let accountsKey = "googleAccounts"
    private static let configuration = OIDServiceConfiguration(
        authorizationEndpoint: URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!,
        tokenEndpoint: URL(string: "https://oauth2.googleapis.com/token")!
    )

    /// Emails of the connected accounts, in the order they were added.
    @Published private(set) var accounts: [String] = []
    @Published private(set) var lastError: String?

    private var states: [String: OIDAuthState] = [:]
    private var currentFlow: OIDExternalUserAgentSession?

    private var clientID: String { Bundle.main.object(forInfoDictionaryKey: "GoogleClientID") as? String ?? "" }
    private var redirectURL: URL? {
        guard let scheme = Bundle.main.object(forInfoDictionaryKey: "GoogleReversedClientID") as? String else { return nil }
        return URL(string: "\(scheme):/oauthredirect")
    }

    /// Loads the connected accounts saved on this device (call on launch).
    func restore() {
        let saved = UserDefaults.standard.stringArray(forKey: Self.accountsKey) ?? []
        for email in saved {
            guard let data = Keychain.load(account: email),
                  let state = try? NSKeyedUnarchiver.unarchivedObject(ofClass: OIDAuthState.self, from: data) else { continue }
            states[email] = state
        }
        accounts = saved.filter { states[$0] != nil }
    }

    /// Shows Google's account chooser and consent screen and adds the chosen account.
    func connect() async {
        lastError = nil
        guard !clientID.isEmpty, !clientID.hasPrefix("missing"), let redirectURL else {
            lastError = "Google sign-in isn't configured in this build (see ios/README.md)."
            return
        }
        guard let presenter = Self.topViewController() else { return }
        let request = OIDAuthorizationRequest(
            configuration: Self.configuration, clientId: clientID, clientSecret: nil,
            scopes: [OIDScopeOpenID, OIDScopeEmail, Self.calendarScope], redirectURL: redirectURL,
            responseType: OIDResponseTypeCode, additionalParameters: ["prompt": "select_account consent"]
        )
        do {
            let state: OIDAuthState = try await withCheckedThrowingContinuation { continuation in
                currentFlow = OIDAuthState.authState(byPresenting: request, presenting: presenter) { state, error in
                    if let state { continuation.resume(returning: state) } else {
                        continuation.resume(throwing: error ?? NotAuthorizedError())
                    }
                }
            }
            currentFlow = nil
            guard state.scope?.contains(Self.calendarScope) ?? Self.grantedCalendar(state) else {
                lastError = "Calendar access is needed to show your meetings."
                return
            }
            guard let email = Self.email(of: state) else {
                lastError = "Couldn't read the account's email address."
                return
            }
            save(state, for: email)
            if !accounts.contains(email) { accounts.append(email) }
            UserDefaults.standard.set(accounts, forKey: Self.accountsKey)
        } catch let error as NSError where error.domain == OIDGeneralErrorDomain
            && error.code == OIDErrorCode.userCanceledAuthorizationFlow.rawValue {
            currentFlow = nil // user closed the sheet
        } catch {
            currentFlow = nil
            lastError = error.localizedDescription
        }
    }

    /// Removes one account and revokes its access.
    func remove(_ email: String) async {
        if let token = states[email]?.refreshToken ?? states[email]?.lastTokenResponse?.accessToken {
            var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/revoke")!)
            request.httpMethod = "POST"
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            request.httpBody = "token=\(token)".data(using: .utf8)
            _ = try? await URLSession.shared.data(for: request)
        }
        states[email] = nil
        Keychain.delete(account: email)
        accounts.removeAll { $0 == email }
        UserDefaults.standard.set(accounts, forKey: Self.accountsKey)
    }

    /// A fresh access token for `email` (refreshed by AppAuth when expired).
    func accessToken(for email: String) async throws -> String {
        guard let state = states[email] else { throw NotAuthorizedError() }
        let token: String = try await withCheckedThrowingContinuation { continuation in
            state.performAction { accessToken, _, error in
                if let accessToken { continuation.resume(returning: accessToken) } else {
                    continuation.resume(throwing: error ?? NotAuthorizedError())
                }
            }
        }
        save(state, for: email) // keep refreshed tokens
        return token
    }

    /// Hands the redirect back to AppAuth (only needed on older flows; ASWebAuthenticationSession handles it).
    func handle(url: URL) -> Bool {
        guard let flow = currentFlow, flow.resumeExternalUserAgentFlow(with: url) else { return false }
        currentFlow = nil
        return true
    }

    private func save(_ state: OIDAuthState, for email: String) {
        states[email] = state
        if let data = try? NSKeyedArchiver.archivedData(withRootObject: state, requiringSecureCoding: true) {
            Keychain.save(data, account: email)
        }
    }

    private static func email(of state: OIDAuthState) -> String? {
        guard let idToken = state.lastTokenResponse?.idToken,
              let claims = OIDIDToken(idTokenString: idToken)?.claims else { return nil }
        return claims["email"] as? String
    }

    private static func grantedCalendar(_ state: OIDAuthState) -> Bool {
        state.lastTokenResponse?.scope?.contains(calendarScope) ?? true
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
