# Google sign-in verification (OAuth)

The app asks for `https://www.googleapis.com/auth/calendar.readonly`, a **sensitive** scope. For anyone
other than your test users to sign in without an "unverified app" warning (and beyond 100 users), Google
must verify the app. It's free and usually takes **3–5 business days**. No security assessment is needed
(that's only for *restricted* scopes like Gmail).

## 1. Prove you own the website domain

The homepage and privacy policy live on `ferrau10.github.io`, which must be an **authorized domain**, and
Google requires you to verify ownership in Search Console:

1. Make sure the website is live: GitHub → repo **Settings → Pages → Source: GitHub Actions**, then
   **Actions → Website → Run workflow**. Check https://ferrau10.github.io/calendar-alarm/ opens.
2. Open [Google Search Console](https://search.google.com/search-console) → **Add property** →
   **URL prefix** → `https://ferrau10.github.io/calendar-alarm/`.
3. Choose **HTML file** verification and download the file (named like `google1234abcd.html`).
4. Send that file name and its one line of content to Claude (or add it to `site/` yourself). Once deployed,
   click **Verify** in Search Console.

## 2. Fill in the consent screen

Google Cloud → **Google Auth Platform → Branding**:

| Field | Value |
|---|---|
| App name | Calendar Alarm |
| User support email | your email |
| App logo | `play/listing/en-US/graphics/icon-512.png` (Google resizes it) |
| Application home page | `https://ferrau10.github.io/calendar-alarm/` |
| Privacy policy link | `https://ferrau10.github.io/calendar-alarm/privacy.html` |
| Authorized domains | `ferrau10.github.io` |
| Developer contact email | your email |

**Data access**: only `.../auth/calendar.readonly`. Remove any other scopes.

**Audience → Publish app** (status *In production*).

## 3. Submit for verification

**Verification center → Prepare for verification / Submit**. Use these answers:

**How will the scopes be used?**

> Calendar Alarm is an Android app that rings an alarm before the Google Calendar meetings the user chooses.
> It uses calendar.readonly to (1) list the user's calendars so they can choose which to show, (2) list
> upcoming events (next 7 days) so the user can pick the meetings that should ring an alarm, and (3)
> re-read selected events periodically so that alarms follow meetings that are moved, renamed or cancelled.
> All data is processed on the device only; the app has no backend and never transmits calendar data to
> the developer or any third party. The app never modifies the calendar.

**Why can't a narrower scope be used?**

> calendar.readonly is already the narrowest scope that allows listing the user's calendars and reading
> event times and titles. calendar.events.readonly would not allow listing the calendars the user can choose
> from, and calendar.freebusy does not include event titles, which the alarm must show.

**Demo video** (YouTube, *Unlisted*; Google is strict about these points):

1. Show the app on the phone and tap **Connect**.
2. The Google account picker and the **consent screen** must be visible, showing the app name
   "Calendar Alarm" and the permission "See and download any calendar you can access…".
3. After allowing, show the events list populated from the calendar (the scope in use).
4. Toggle an alarm on an event and show **Settings → Test alarm** ringing, to show what the data is for.
5. Optionally show **Settings → Disconnect**.

Google may email follow-up questions; answer them in the same thread.

## Until verification is done

With the consent screen *In production* but unverified, up to 100 users can sign in after tapping
**Advanced → Go to Calendar Alarm (unsafe)**. That's fine for the closed test with your 12 testers.
