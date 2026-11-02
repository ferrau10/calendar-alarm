# Google Play Console: answers to copy and paste

Play Console → your app → **Policy and programs → App content** (and **Monitor and improve → Policy status**
for the permission declarations). Answers below match what the app actually does; keep them in sync if
features change.

## Before you submit for review: Google sign-in must work for the reviewer

Reviewers sign in with their own Google account. While the OAuth consent screen is in **Testing**, only
listed test users can sign in, so the reviewer would be blocked.

- Google Cloud → **Google Auth Platform → Audience → Publish app** (status *In production*). Until Google's
  verification is done (see [oauth-verification.md](oauth-verification.md)) people see an "unverified app"
  warning and can continue via *Advanced*; that's acceptable for testing tracks.
- Make sure the Android OAuth client for **Play's app-signing key** exists ([release.md](release.md#google-play)).

## App access

**All or some functionality is restricted** → add instructions:

> Name: Google sign-in
> Instructions: The app reads the user's own Google Calendar. Tap "Get started", then "Connect" and sign in
> with any Google account (a personal Gmail account works) and allow "See your calendars". If the account's
> calendar is empty, create an event in Google Calendar for later today; it appears in the app's list.
> Tap the switch next to the event to set an alarm. To hear an alarm immediately: Settings (gear icon) →
> "Test alarm" rings after 10 seconds.
> No username/password of ours is needed; there is no account system in the app.

## Ads

**No**, the app does not contain ads.

## Content rating

Questionnaire → category **All other app types** (utility/productivity). Answer **No** to every content
question (violence, sexuality, language, controlled substances, gambling, user-generated content sharing,
location sharing, purchases). Expected rating: Everyone / PEGI 3.

## Target audience and content

- Target age groups: **18 and over** (simplest: the app isn't designed for children, and choosing younger
  groups brings the Families policy into scope).
- Appeals to children: **No**.

## News app / COVID-19 / Government / Financial / Health

All **No**.

## Data safety

Google's rule: data that is only processed on the device and not sent off it is **not** "collected".
Calendar Alarm reads calendar data from Google into the phone and keeps it there; nothing goes to us or any
third party.

- Does your app collect or share any of the required user data types? **No**
- (If Play asks about the Google sign-in: the account email and calendar data are accessed on-device only,
  via Google's APIs, and never transmitted to the developer.)
- Privacy policy URL: `https://ferrau10.github.io/calendar-alarm/privacy.html`

## Permission declarations

### Exact alarms (`USE_EXACT_ALARM`)

Play allows this permission only for apps whose core function is an alarm clock or calendar.
Declaration → **core functionality: Alarm clock**:

> Calendar Alarm is an alarm clock for meetings: its core and only function is to ring an alarm at an exact,
> user-chosen time (e.g. 5 minutes) before calendar events the user selects. Inexact alarms would ring late
> and make the app useless, so exact alarms are essential.

### Full-screen intent (`USE_FULL_SCREEN_INTENT`, Android 14+)

Declaration → **Alarm clock**:

> When a meeting alarm goes off, the app shows a full-screen alarm over the lock screen (like the system
> clock app) with the meeting name and Snooze / Dismiss buttons. This is the app's core alarm functionality.

### Foreground service (`systemExempted`)

**Policy and programs → App content → Foreground service permissions** → select *System exempted* (or the
type Play lists for alarm apps) and describe:

> While an alarm is ringing, a short foreground service plays the alarm sound and vibration until the user
> dismisses or snoozes it (max. 1 minute). Android allows the systemExempted type for apps holding
> USE_EXACT_ALARM that use a foreground service to continue an alarm. It is started only by an exact alarm
> set by the user and stops when the alarm is dismissed.

Video link: see the script below (one video covers all three declarations).

### Demo video script (screen recording, 30–60 s, upload to YouTube as *Unlisted*)

1. Open Calendar Alarm, show the list of upcoming events.
2. Tap the switch on an event; show "Alarm at … (5 min before)". Tap **Change**, pick a time.
3. Open **Settings → Test alarm**, lock the phone.
4. Show the full-screen alarm appearing on the lock screen with sound; tap **Snooze**, then let it ring again
   and tap **Dismiss**.

On Android: Quick settings → **Screen record** (with audio) records this.

## Store listing

See [play/listing/README.md](../play/listing/README.md).
