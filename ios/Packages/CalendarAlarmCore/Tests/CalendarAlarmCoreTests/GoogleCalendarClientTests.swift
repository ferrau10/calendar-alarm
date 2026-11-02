import XCTest
@testable import CalendarAlarmCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Answers requests from a handler instead of the network.
final class StubURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) -> (Int, String))?
    nonisolated(unsafe) static var requests: [URLRequest] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.requests.append(request)
        let (status, body) = Self.handler?(request) ?? (500, "{}")
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

final class GoogleCalendarClientTests: XCTestCase {
    private var tokenCalls: [Bool] = []
    private let lock = NSLock()

    override func setUp() {
        StubURLProtocol.requests = []
        StubURLProtocol.handler = nil
        tokenCalls = []
    }

    private func client() -> GoogleCalendarClient {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return GoogleCalendarClient(
            session: URLSession(configuration: config),
            baseURL: URL(string: "https://example.test/calendar/v3")!,
            timeZone: { TimeZone(identifier: "Europe/Madrid")! },
            tokens: { [weak self] force in
                self?.lock.withLock { self?.tokenCalls.append(force) }
                return force ? "fresh" : "token"
            }
        )
    }

    private func date(_ s: String) -> Date { ISO8601DateFormatter().date(from: s)! }
    private let from = ISO8601DateFormatter().date(from: "2026-11-02T00:00:00Z")!
    private let to = ISO8601DateFormatter().date(from: "2026-11-09T00:00:00Z")!

    func testCalendarsMapFieldsAndFollowPages() async throws {
        StubURLProtocol.handler = { req in
            req.url!.query?.contains("pageToken=p2") == true
                ? (200, #"{"items":[{"id":"team","summary":"Team","summaryOverride":"My team"}]}"#)
                : (200, ##"{"nextPageToken":"p2","items":[{"id":"me@example.com","summary":"Me","backgroundColor":"#9fe1e7","primary":true}]}"##)
        }
        let calendars = try await client().calendars()
        XCTAssertEqual(calendars.map(\.id), ["me@example.com", "team"])
        XCTAssertEqual(calendars[1].name, "My team")
        XCTAssertTrue(calendars[0].primary)
        XCTAssertEqual(StubURLProtocol.requests.first?.value(forHTTPHeaderField: "Authorization"), "Bearer token")
        XCTAssertEqual(StubURLProtocol.requests.first?.url?.path, "/calendar/v3/users/me/calendarList")
    }

    func testEventsMapTimedAllDayCancelledAndAttendees() async throws {
        StubURLProtocol.handler = { _ in (200, #"""
        {"items":[
          {"id":"allday","start":{"date":"2026-11-02"},"end":{"date":"2026-11-03"}},
          {"id":"standup","summary":"Standup","start":{"dateTime":"2026-11-02T09:00:00+01:00"},"end":{"dateTime":"2026-11-02T09:15:00+01:00"},
           "hangoutLink":"https://meet.google.com/abc","attendees":[{"self":true,"responseStatus":"accepted"},{"email":"sam@example.com"}]},
          {"id":"gone","status":"cancelled","summary":"Gone","start":{"dateTime":"2026-11-02T10:00:00Z"},"end":{"dateTime":"2026-11-02T11:00:00Z"}},
          {"id":"zoom","summary":"Planning","start":{"dateTime":"2026-11-02T14:00:00Z"},"end":{"dateTime":"2026-11-02T15:00:00Z"},
           "conferenceData":{"entryPoints":[{"entryPointType":"phone","uri":"tel:1"},{"entryPointType":"video","uri":"https://zoom.example/j/1"}]},
           "attendees":[{"self":true,"responseStatus":"declined"},{"email":"boss@example.com","organizer":true}]}
        ]}
        """#) }

        let events = try await client().upcomingEvents(calendarIds: ["primary"], from: from, to: to)

        XCTAssertEqual(events.map(\.id), ["allday", "standup", "zoom"])
        XCTAssertTrue(events[0].allDay)
        XCTAssertEqual(events[0].title, "(No title)")
        XCTAssertEqual(events[0].start, date("2026-11-01T23:00:00Z")) // midnight in Madrid
        XCTAssertEqual(events[1].start, date("2026-11-02T08:00:00Z"))
        XCTAssertEqual(events[1].meetLink, "https://meet.google.com/abc")
        XCTAssertTrue(events[1].hasOtherAttendees)
        XCTAssertTrue(events[1].acceptedByMe)
        XCTAssertEqual(events[2].meetLink, "https://zoom.example/j/1")
        XCTAssertFalse(events[2].acceptedByMe)

        let query = StubURLProtocol.requests.first!.url!.query!
        XCTAssertTrue(query.contains("singleEvents=true"))
        XCTAssertTrue(query.contains("timeMin=2026-11-02T00:00:00Z"))
    }

    func testCalendarIdIsEncodedAsOneSegment() async throws {
        StubURLProtocol.handler = { _ in (200, #"{"items":[]}"#) }
        _ = try await client().upcomingEvents(calendarIds: ["abc#holiday@group.v.calendar.google.com"], from: from, to: to)
        XCTAssertTrue(StubURLProtocol.requests.first!.url!.absoluteString
            .contains("/calendars/abc%23holiday@group.v.calendar.google.com/events"))
    }

    func testEventReturnsNilWhenDeletedOrCancelled() async throws {
        StubURLProtocol.handler = { req in
            if req.url!.path.hasSuffix("/gone") { return (410, "{}") }
            if req.url!.path.hasSuffix("/missing") { return (404, "{}") }
            return (200, #"{"id":"x","status":"cancelled","start":{"dateTime":"2026-11-02T10:00:00Z"}}"#)
        }
        let c = client()
        let gone = try await c.event(calendarId: "primary", eventId: "gone")
        let missing = try await c.event(calendarId: "primary", eventId: "missing")
        let cancelled = try await c.event(calendarId: "primary", eventId: "x")
        XCTAssertNil(gone)
        XCTAssertNil(missing)
        XCTAssertNil(cancelled)
    }

    func testRetriesOnceWithFreshTokenOn401() async throws {
        StubURLProtocol.handler = { req in
            req.value(forHTTPHeaderField: "Authorization") == "Bearer token"
                ? (401, "{}")
                : (200, #"{"id":"p","summary":"Planning","start":{"dateTime":"2026-11-03T14:30:00Z"},"end":{"dateTime":"2026-11-03T15:30:00Z"}}"#)
        }
        let event = try await client().event(calendarId: "primary", eventId: "p")
        XCTAssertEqual(event?.title, "Planning")
        XCTAssertEqual(tokenCalls, [false, true])
    }

    func testOtherErrorsThrow() async {
        StubURLProtocol.handler = { _ in (403, #"{"error":{"message":"Rate Limit Exceeded"}}"#) }
        do {
            _ = try await client().calendars()
            XCTFail("expected an error")
        } catch let error as CalendarAPIError {
            XCTAssertEqual(error.status, 403)
            XCTAssertTrue(error.body.contains("Rate Limit"))
        } catch {
            XCTFail("unexpected \(error)")
        }
    }
}
