import Foundation

/// `CalendarRepository` over every connected account (port of the Android `MultiAccountCalendarRepository`):
/// asks each account with its own token, tags results with the account, merges and de-duplicates them.
/// One failing account (e.g. revoked) doesn't hide the others.
public actor MultiAccountCalendarRepository: CalendarRepository {
    private let accounts: @Sendable () async -> [String]
    private let apiFor: @Sendable (String) -> AccountCalendarAPI
    /// calendarId → account that lists it, from the latest `calendars()` call.
    private var owners: [String: String] = [:]

    public init(accounts: @escaping @Sendable () async -> [String], apiFor: @escaping @Sendable (String) -> AccountCalendarAPI) {
        self.accounts = accounts
        self.apiFor = apiFor
    }

    public func calendars() async throws -> [CalendarInfo] {
        let all = try await perAccount(try await connected()) { account, api in
            try await api.calendars().map { var c = $0; c.account = account; return c }
        }
        var seen = Set<String>()
        let unique = all.flatMap { $0 }.filter { seen.insert($0.id).inserted }
        for calendar in unique { owners[calendar.id] = calendar.account }
        return unique
    }

    public func upcomingEvents(calendarIds: Set<String>, from: Date, to: Date) async throws -> [CalendarEvent] {
        let connected = try await connected()
        if calendarIds.contains(where: { $0 != "primary" && owners[$0] == nil }) { _ = try? await calendars() }
        var byAccount: [String: Set<String>] = [:]
        for id in calendarIds {
            guard let account = id == "primary" ? connected.first : owners[id], connected.contains(account) else { continue }
            byAccount[account, default: []].insert(id)
        }
        guard !byAccount.isEmpty else { return [] }
        let results = try await perAccount(Array(byAccount.keys)) { account, api in
            try await api.upcomingEvents(calendarIds: byAccount[account] ?? [], from: from, to: to)
                .map { var e = $0; e.account = account; return e }
        }
        var seen = Set<String>()
        return results.flatMap { $0 }
            .filter { seen.insert($0.id).inserted }
            .sorted { ($0.start, $0.title) < ($1.start, $1.title) }
    }

    public func event(account: String?, calendarId: String, eventId: String) async throws -> CalendarEvent? {
        let connected = try await connected()
        let chosen = [account, owners[calendarId]].compactMap { $0 }.first(where: { connected.contains($0) }) ?? connected[0]
        guard var event = try await apiFor(chosen).event(calendarId: calendarId, eventId: eventId) else { return nil }
        event.account = chosen
        return event
    }

    private func connected() async throws -> [String] {
        let list = await accounts()
        if list.isEmpty { throw NotAuthorizedError() }
        return list
    }

    /// Runs `block` for each account in parallel; succeeds if any account does, else rethrows the first error.
    private func perAccount<T: Sendable>(
        _ accounts: [String], _ block: @escaping @Sendable (String, AccountCalendarAPI) async throws -> T
    ) async throws -> [T] {
        let apiFor = self.apiFor
        let outcomes: [(Int, Result<T, Error>)] = await withTaskGroup(of: (Int, Result<T, Error>).self) { group in
            for (index, account) in accounts.enumerated() {
                group.addTask {
                    do { return (index, .success(try await block(account, apiFor(account)))) } catch { return (index, .failure(error)) }
                }
            }
            var collected: [(Int, Result<T, Error>)] = []
            for await outcome in group { collected.append(outcome) }
            return collected.sorted { $0.0 < $1.0 }
        }
        let successes = outcomes.compactMap { try? $0.1.get() }
        if successes.isEmpty, let first = outcomes.first { _ = try first.1.get() }
        return successes
    }
}
