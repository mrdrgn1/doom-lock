# Changelog

Every build of Lock In, newest first. Newer builds install straight over older ones and keep
your sessions, notes and settings.

This trail was rebuilt from the APKs kept in `dist/`, so it lists what each build actually
shipped. Builds 1.0–1.8 are recorded as commits carrying their shipped APKs, because the source
for those builds was never kept anywhere but inside the APKs. From 1.9 on, each build is a
normal commit plus a tag.


## 1.8

- **Quick panel pass.** A `QUICK PANEL · 1 MIN` strip on the focus screen releases the pin for a
  minute so the quick panel comes down. Focus freezes, the minute counts as unlocked time, the
  strip counts down, and the screen pins itself again when it is up.
- **Allowed apps are chosen by you.** Three slots with a picker (`Allowed apps` on the start
  screen), shown with their own icons on the focus screen. Spotify, WhatsApp and Phone stay the
  defaults until you change them; hold a slot to clear it.
- **Daily reminder**, off by default: pick a time and it posts a nudge with START and NOT TODAY.
  It only asks, it never starts a session by itself.
- **Weekly review** (tap the week card): week total, session count and average, days on target,
  the hour you focus most, doomscroll earned vs. used, and every day's total.
- **Streak freezes**: two a week, unused ones carry over. A single missed day spends one and the
  streak survives.
- **LOCK IN button on the widget**, hidden while a session is running.
- Hitting the session goal gives a short double buzz and the goal line flashes.
- The setup list folds away as soon as do-not-disturb and display-over-apps are on.

## 1.7

- Start screen opens on a **recap of the last session** (length, when it ran, share of it locked)
  instead of today's running total. Today's progress moved into the daily target line.
- The big dot-matrix **LOCK IN** headline came off the start screen.
- The setup list folds into one line with a small SHOW / HIDE button, and remembers your choice.

## 1.6

- **Daily target** with a 24-dot progress row; tap the line to cycle 1h / 2h / 3h / 4h / 6h / off.
- **Seven-bar week chart**, Monday first, today in red.
- **UNDO** bar for six seconds after deleting, adding or editing a log.
- **Edit a logged session**; its focus time becomes end minus start.
- The widget switches to the live session state — LOCKED IN, ON A BREAK, PAUSED, IN AN APP — with
  the doomscroll balance instead of today's total.
- Hold buttons tap, tick after a second and confirm with haptics.

## 1.5

- Notification buttons: **BACK TO LOCK IN**, **RESUME**, **END**, handled without opening the app.
- **Quick Settings tile** that starts a session from the shade.
- Calendar day view draws each session as a red/grey timeline.
- Dot-matrix **LOCK IN** headline on the start screen.

## 1.4

- Calendar lists every session logged that day, with **add** (typed start and end, overnight
  allowed) and **delete** with a confirmation.
- Doomscroll time resets to 10 minutes every session, undoing the 1.2 experiment.
- Database version 2: a `segments` table, added without touching existing sessions.

## 1.3

- **End-of-session summary**: time locked in, time unlocked (break + pause), what share of the
  session was locked, and a timeline bar showing when each happened.
- **Home screen widget** with today's focus total and session count.
- Sessions started saving their timeline, so the summary can draw it.

## 1.2

- Doomscroll time carried over between sessions. Reverted in 1.4: a session starts at 10 minutes
  again, earning 1 minute per 6 minutes of focus, uncapped.

## 1.1

- **Session length chips** (30M / 1H / 1.5H / 2H / 3H / OPEN) and a hollow dot rectangle that
  fills in as the session works towards its goal.
- **Now playing** line on the focus screen, with a next-track button.
- **This week** and **streak** lines on the start screen.

## 1.0

- First build: start screen, pinned focus screen with dimming and burn-in shift, doomscroll
  breaks, always-available apps, pause with reminders, hold-to-end, calendar with autosaving
  notes, and do-not-disturb switched on for the session and restored afterwards.
