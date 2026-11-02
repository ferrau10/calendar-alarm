import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Read access to Google Calendar (mirrors the Android `CalendarRepository`).
public protocol CalendarRepository: Sendable {
    func calendars() async throws -> [CalendarInfo]
    /// Events overlapping [from, to) across the calendars, sorted by start time.
    func upcomingEvents(calendarIds: Set<String>, from: Date, to: Date) async throws -> [CalendarEvent]
    /// The latest version of an event, or nil when it was deleted or cancelled.
    func event(calendarId: String, eventId: String) async throws -> CalendarEvent?
}

/// Thrown when the user isn't signed in or access was revoked.
public struct NotAuthorizedError: Error, Equatable {
    public init() {}
}

public struct CalendarAPIError: Error, Equatable, CustomStringConvertible {
    public let status: Int
    public let body: String
    public var description: String { "Calendar API error \(status): \(body)" }
}

/// Google Calendar REST API v3 client.
public final class GoogleCalendarClient: CalendarRepository, @unchecked Sendable {
    /// Returns an access token; `forceRefresh` is true after the API rejected the previous one (HTTP 401).
    public typealias TokenProvider = @Sendable (_ forceRefresh: Bool) async throws -> String

    private let session: URLSession
    private let tokens: TokenProvider
    private let baseURL: URL
    private let timeZone: @Sendable () -> TimeZone

    public init(
        session: URLSession = .shared,
        baseURL: URL = URL(string: "https://www.googleapis.com/calendar/v3")!,
        timeZone: @escaping @Sendable () -> TimeZone = { .current },
        tokens: @escaping TokenProvider
    ) {
        self.session = session
        self.baseURL = baseURL
        self.timeZone = timeZone
        self.tokens = tokens
    }

    public func calendars() async throws -> [CalendarInfo] {
        var result: [CalendarInfo] = []
        var pageToken: String?
        repeat {
            var query: [URLQueryItem] = []
            if let pageToken { query.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            let page: CalendarListDTO = try await get(["users", "me", "calendarList"], query) ?? CalendarListDTO()
            result += page.items.map {
                CalendarInfo(id: $0.id, name: $0.summaryOverride ?? $0.summary ?? $0.id,
                             colorHex: $0.backgroundColor, primary: $0.primary ?? false)
            }
            pageToken = page.nextPageToken
        } while pageToken != nil
        return result
    }

    public func upcomingEvents(calendarIds: Set<String>, from: Date, to: Date) async throws -> [CalendarEvent] {
        var all: [CalendarEvent] = []
        try await withThrowingTaskGroup(of: [CalendarEvent].self) { group in
            for id in calendarIds {
                group.addTask { try await self.events(of: id, from: from, to: to) }
            }
            for try await events in group { all += events }
        }
        return all.sorted { ($0.start, $0.title) < ($1.start, $1.title) }
    }

    public func event(calendarId: String, eventId: String) async throws -> CalendarEvent? {
        guard let dto: EventDTO = try await get(["calendars", calendarId, "events", eventId], []) else { return nil }
        return toDomain(dto, calendarId: calendarId)
    }

    private func events(of calendarId: String, from: Date, to: Date) async throws -> [CalendarEvent] {
        var result: [CalendarEvent] = []
        var pageToken: String?
        let iso = ISO8601DateFormatter()
        repeat {
            var query = [
                URLQueryItem(name: "singleEvents", value: "true"),
                URLQueryItem(name: "orderBy", value: "startTime"),
                URLQueryItem(name: "timeMin", value: iso.string(from: from)),
                URLQueryItem(name: "timeMax", value: iso.string(from: to)),
                URLQueryItem(name: "maxResults", value: "250"),
            ]
            if let pageToken { query.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            let page: EventsDTO = try await get(["calendars", calendarId, "events"], query) ?? EventsDTO()
            result += page.items.compactMap { toDomain($0, calendarId: calendarId) }
            pageToken = page.nextPageToken
        } while pageToken != nil
        return result
    }

    /// GET returning the decoded body, nil for 404/410; retries once with a fresh token on 401.
    private func get<T: Decodable>(_ path: [String], _ query: [URLQueryItem]) async throws -> T? {
        var (data, status) = try await send(path, query, token: tokens(false))
        if status == 401 { (data, status) = try await send(path, query, token: tokens(true)) }
        switch status {
        case 200..<300: return try JSONDecoder().decode(T.self, from: data)
        case 404, 410: return nil
        case 401: throw NotAuthorizedError()
        default: throw CalendarAPIError(status: status, body: String(decoding: data.prefix(500), as: UTF8.self))
        }
    }

    private func send(_ path: [String], _ query: [URLQueryItem], token: String) async throws -> (Data, Int) {
        // Each segment is percent-encoded on its own (calendar ids contain '@' and '#').
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "/#?"))
        let encodedPath = path.map { $0.addingPercentEncoding(withAllowedCharacters: allowed) ?? $0 }.joined(separator: "/")
        var components = URLComponents(string: baseURL.absoluteString + "/" + encodedPath)!
        if !query.isEmpty { components.queryItems = query }
        var request = URLRequest(url: components.url!)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
    }

    private func toDomain(_ dto: EventDTO, calendarId: String) -> CalendarEvent? {
        if dto.status == "cancelled" { return nil }
        guard let startDTO = dto.start, let start = date(startDTO) else { return nil }
        let end = dto.end.flatMap(date) ?? start
        let me = dto.attendees?.first { $0.selfFlag == true }
        let others = dto.attendees?.contains { $0.selfFlag != true && $0.resource != true } ?? false
        let accepted: Bool
        if let me {
            accepted = me.responseStatus == "accepted" || (me.organizer == true && me.responseStatus == nil)
        } else {
            accepted = (dto.attendees ?? []).isEmpty // no attendee list: my own event
        }
        let title = dto.summary?.trimmingCharacters(in: .whitespaces).isEmpty == false ? dto.summary! : "(No title)"
        return CalendarEvent(
            id: dto.id, calendarId: calendarId, title: title, start: start, end: end,
            allDay: startDTO.dateTime == nil && startDTO.date != nil,
            location: dto.location?.isEmpty == false ? dto.location : nil,
            meetLink: dto.hangoutLink ?? dto.conferenceData?.entryPoints?.first { $0.entryPointType == "video" }?.uri,
            hasOtherAttendees: others, acceptedByMe: accepted
        )
    }

    private func date(_ dto: EventDateTimeDTO) -> Date? {
        if let dateTime = dto.dateTime {
            let full = ISO8601DateFormatter()
            if let d = full.date(from: dateTime) { return d }
            full.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            return full.date(from: dateTime)
        }
        if let day = dto.date {
            let formatter = DateFormatter()
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = timeZone()
            formatter.dateFormat = "yyyy-MM-dd"
            return formatter.date(from: day) // local midnight
        }
        return nil
    }
}

// MARK: - API resources (subset)

struct CalendarListDTO: Decodable {
    var items: [CalendarListEntryDTO] = []
    var nextPageToken: String?
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = try c.decodeIfPresent([CalendarListEntryDTO].self, forKey: .items) ?? []
        nextPageToken = try c.decodeIfPresent(String.self, forKey: .nextPageToken)
    }
    enum CodingKeys: String, CodingKey { case items, nextPageToken }
}

struct CalendarListEntryDTO: Decodable {
    let id: String
    let summary: String?
    let summaryOverride: String?
    let backgroundColor: String?
    let primary: Bool?
}

struct EventsDTO: Decodable {
    var items: [EventDTO] = []
    var nextPageToken: String?
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = try c.decodeIfPresent([EventDTO].self, forKey: .items) ?? []
        nextPageToken = try c.decodeIfPresent(String.self, forKey: .nextPageToken)
    }
    enum CodingKeys: String, CodingKey { case items, nextPageToken }
}

struct EventDTO: Decodable {
    let id: String
    let status: String?
    let summary: String?
    let location: String?
    let start: EventDateTimeDTO?
    let end: EventDateTimeDTO?
    let hangoutLink: String?
    let conferenceData: ConferenceDataDTO?
    let attendees: [AttendeeDTO]?
}

struct EventDateTimeDTO: Decodable {
    let dateTime: String?
    let date: String?
}

struct ConferenceDataDTO: Decodable {
    let entryPoints: [EntryPointDTO]?
}

struct EntryPointDTO: Decodable {
    let entryPointType: String?
    let uri: String?
}

struct AttendeeDTO: Decodable {
    let selfFlag: Bool?
    let resource: Bool?
    let responseStatus: String?
    let organizer: Bool?
    enum CodingKeys: String, CodingKey {
        case selfFlag = "self", resource, responseStatus, organizer
    }
}
