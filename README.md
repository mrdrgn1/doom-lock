# Doom Lock

The project is called Doom Lock. On the phone the app shows up as **Lock In** — that is the name
it had from 1.0 to 1.8, and 1.9's rename is undone in 1.10. The app id is still `com.lockin`, so
any build installs over any other and keeps history.

A focus app for library and uni sessions: it pins itself to the screen, silences the phone,
dims to an AOD-style black timer, and blocks the rest of the phone until you end the session.

Kotlin, Gradle Kotlin DSL, plain Android views, no third-party libraries, `minSdk 26`,
`targetSdk 35`.

Every build and what it added is in [CHANGELOG.md](CHANGELOG.md); the installable APKs are kept
in `dist/`. Every APK is labelled *Lock In* on the phone except 1.9's, which carried the
Doom Lock name.

## Build

Open the folder in Android Studio and press Run, or:

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # signed with the local debug key, also installable
```

## First-run setup (all reachable from the start screen)

1. **Do Not Disturb access** — Settings › Notifications › Device & app notifications ›
   Lock In → allow. Without it the app cannot silence the phone.
2. **Display over other apps** — Settings › Apps › Special access › Display over other apps ›
   Lock In → allow. This is what lets a finished doomscroll break pull the app back to the front.
3. **Notification access** — Settings › Notifications › Device & app notifications ›
   Lock In → allow. Only used to read what music app is playing, so the focus screen can show
   the track and a next button. Optional: without it the now-playing line stays hidden.
4. **Screen pinning + PIN** — Settings › Security and privacy › Other security settings ›
   Pin windows → on, and choose “Ask for PIN before unpinning”. The focus screen calls
   `startLockTask()`, so the PIN is your emergency way out.
5. Allow notifications (Android 13+) so break countdowns and pause reminders show up.

On Samsung the overlay entry can be under *Apps › Special access*; the start screen buttons
open the exact settings pages.

## How the session behaves

| State | Focus timer | Doomscroll bank | DND | Pinned |
| --- | --- | --- | --- | --- |
| Focus | runs | starts at 10 min, +1 min per 6 min, no cap | on (alarms only) | yes |
| Doomscroll | frozen | drains in real time, only used time charged | off | no |
| Quick panel | frozen, one minute | untouched | on | no |
| Pause (hold 2s) | frozen | frozen | off | no |
| Allowed app (10 min) | runs | untouched | on | no |

- **Session length** is picked on the start screen (30m / 1h / 1.5h / 2h / 3h / open). With a goal,
  the focus screen shows a hollow rectangle of dots that fills in row by row as the session runs;
  when it is full the line under it turns red and says the goal is done. The session keeps running
  until you hold end.
- If music is playing, a **now playing** line shows the track, the artist and a next-track button.
- Every session starts with 10 minutes of doomscroll time and earns 1 more minute for every
6 minutes of focus. There is no cap, so a long stretch saves up a long break, and a break only
ever costs the minutes you actually used. Ending the session resets the bank to 10 minutes.
- Ending a session opens a summary: time locked in, time unlocked (break + pause), and a timeline
bar showing exactly when each happened. Sessions saved before this version simply show no
segment detail.
- A home screen widget shows today's focus total and session count, and a Quick Settings tile
  locks in straight from the shade. The break and pause notifications carry buttons
  (BACK TO LOCK IN, RESUME, END) so you never have to open the app to press them.
- The calendar day view draws the same red/grey timeline bar as the end-of-session screen.
- Deleting, adding or editing a log shows an **UNDO** bar for six seconds. Tapping a session row
  opens it for editing; its focus time becomes end minus start and its timeline is dropped.
- The start screen opens on a recap of the session you just finished (its length, when it ran and
  how much of it was locked) instead of today's running total, followed by the week and streak
  lines and a seven-bar chart with today in red. Today's own progress lives in the daily target
  line (tap it to cycle 1h / 2h / 3h / 4h / 6h / off) and its 24-dot row.
- The setup list folds itself away once Do Not Disturb and display over other apps are on.
  A small SHOW / HIDE button on the SETUP line brings it back, and that choice is remembered.
- The widget follows the session: during focus it shows the running time and the doomscroll
  balance instead of today's total, and repaints on every state change. It also carries a small
  **LOCK IN** button that starts a session straight from the home screen (hidden while running).
- **QUICK PANEL · 1 MIN** on the focus screen releases the pin for one minute so the quick panel
  comes down. Focus freezes and the minute counts as unlocked time, so it never inflates the
  session; the row shows a live countdown and tapping it again returns early. When the minute is
  up the service ends the pass and the screen pins itself again.
- **Allowed apps** are three slots you choose (start screen › Allowed apps). Tap a slot to pick
  an app, hold it to clear the slot. They show up with their own icons on the focus screen and
  cost no doomscroll time.
- **Daily reminder** (off by default): pick a time and the app posts a nudge with START and
  NOT TODAY. It only ever asks, never starts a session by itself.
- Tapping the week card opens the **weekly review**: total, average session, days on target, the
  hour you focus most, doomscroll earned vs. used, and each day's total.
- **Streak freezes**: two a week and unused ones carry over. A single missed day spends one and
  the streak survives; the start screen says how many are in the bank.
- Hitting the session goal gives a short double buzz and the goal line flashes.
- In the calendar, every logged session has a `×` to delete it (with a confirmation), and
  **ADD LOG** lets you type a missed session in as start and end time (`19:00` → `21:30`,
  an end before the start is treated as overnight). Hand-added sessions count towards the day
  and month totals like real ones.
- Tapping the black screen raises brightness for 4 seconds.
- Content shifts a few pixels every minute to avoid burn-in.
- A break that runs out is ended by the foreground service, which restores DND, re-pins and
  brings the app back (overlay permission, with a full-screen notification as backup).
- Pause reminders: one at 30 minutes, then every 5 minutes.
- Hold **end** for 3 seconds to finish: DND and brightness are restored and the session is saved
  to the calendar.

## Project layout

```
app/src/main/java/com/lockin/
  MainActivity.kt        start screen, permissions, last-session recap, week / streak, length
  LockActivity.kt        locked-in screen, pinning, dimming, burn-in shift, doomscroll, pause, end
  PauseActivity.kt       pause timer, resume, hold-to-end
  CalendarActivity.kt    month grid, per-day sessions (add / delete), autosaving note
  SessionStore.kt        persisted session state machine (focus/break/pause/apps, bank)
  SessionService.kt      foreground service: break / app-window countdown and pause reminders
  Dnd.kt                 DND on (alarms only), off, restore previous filter
  HistoryDb.kt           SQLite sessions + notes
  Notifications.kt       channels, ongoing countdown, full-screen return
  BringToFront.kt        overlay-permission activity launch with notification fallback
  AllowedApps.kt         the three user-chosen allowed-app slots
  AllowedAppsActivity.kt picker for those slots
  WeekActivity.kt        weekly review: totals, best hour, doomscroll, freezes
  Freezes.kt             streak freeze bank (two a week, unused carries over)
  Reminder.kt            daily nudge alarm and receiver (asks, never auto-starts)
  MediaWatcher.kt        notification listener that reports the playing track (notification access)
  Fmt.kt                 time formatting
  ui/DotMatrixView.kt    5x7 dot-matrix digits for the timers
  ui/DotGridProgressView.kt  hollow dot rectangle that fills in as the session goal is reached
  ui/HoldButton.kt       hold-to-confirm button with progress fill
```

Data lives in `lockin.db` (sessions, segments, notes) and `lockin_session` SharedPreferences.

## Updating without losing history

Every build is signed with the same debug key and keeps the same application id, so installing a
newer APK over an older one is a plain update: `lockin.db`, notes and preferences are left alone.
The database version only ever adds tables (v1 → v2 added `segments`), so nothing is dropped.
Uninstalling the app is the only thing that deletes the history.
