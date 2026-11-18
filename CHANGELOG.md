# Changelog

Every build of Lock In, newest first. Newer builds install straight over older ones and keep
your sessions, notes and settings.

This trail was rebuilt from the APKs kept in `dist/`, so it lists what each build actually
shipped. Builds 1.0–1.8 are recorded as commits carrying their shipped APKs, because the source
for those builds was never kept anywhere but inside the APKs. From 1.9 on, each build is a
normal commit plus a tag.


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
