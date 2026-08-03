# Play-Budget: Warning Before Expiry Instead of After — Design

- **Date:** 2026-08-03
- **Status:** Draft (pending implementation plan)
- **Owner:** Friedrich
- **Amends:** `2026-05-11-matheaufgaben-play-budget-design.md` — changes the timing (not the existence) of the 5-minute warning within the existing `budget/` package.

## Problem

The current state machine (`WAITING_FOR_WORLD → WAITING_FOR_BUDGET → ACTIVE → EXPIRED → HARD_TIMEOUT`)
shows the "5 Minuten" warning screen only *after* the chosen budget has fully elapsed, then adds a
further 5-minute grace period before `HARD_TIMEOUT`. Net effect: the kid always plays
`chosen budget + 5 minutes`, and the warning ("noch 5 Minuten") is misleading — it announces extra
time being granted rather than warning that time is running out.

## Goal

The warning must fire 5 minutes *before* the budget runs out, as an early heads-up, and
`HARD_TIMEOUT` must land exactly at the chosen budget. Total playtime becomes exactly the
budget the kid selected — no more, no less.

## Approach

Reuse the existing `EXPIRED` phase slot in the state machine, but move its trigger point earlier
and drop the extra grace allowance:

- `ACTIVE → WARNING` (renamed from `EXPIRED`) at `budgetTicks - WARNING_TICKS` elapsed, instead of
  at `budgetTicks` elapsed.
- `WARNING → HARD_TIMEOUT` at `budgetTicks` elapsed (the actual chosen budget), instead of
  `budgetTicks + GRACE_TICKS`.
- `WARNING_TICKS` (renamed from `GRACE_TICKS`) keeps its value: 5 minutes / 6000 ticks. It's no
  longer *added* time — it's a window carved out of the existing budget.

This is a rename + a shift of the trigger point, not a new state or new config surface. No JSON
config changes, no new screens.

### Edge case: budget ≤ 5 minutes

If the chosen budget is at or under `WARNING_TICKS` (5 minutes), there's no room for a warning
window before expiry. In that case `ACTIVE` transitions directly to `HARD_TIMEOUT` at
`budgetTicks` elapsed, skipping `WARNING` entirely. This only matters for very short "Eigene Zeit"
entries (e.g. 3 minutes); the 30/60-minute presets are unaffected.

### `remainingTicks()` simplifies

Previously `EXPIRED`'s remaining-ticks formula was `budgetTicks + GRACE_TICKS - elapsedTicks`
(counting down the grace add-on). Since `WARNING` no longer adds time, both `ACTIVE` and
`WARNING` now share the same formula: `budgetTicks - elapsedTicks`. `remainingTicks()` no longer
needs to branch on phase for these two cases.

## Renames

| Before | After |
|---|---|
| `BudgetPhase.EXPIRED` | `BudgetPhase.WARNING` |
| `BudgetState.GRACE_TICKS` | `BudgetState.WARNING_TICKS` |
| `BudgetSoftExpiredScreen` | `BudgetWarningScreen` |

Rationale: keeping the old names would leave "EXPIRED" and "grace" describing a phase that now
fires *before* anything has expired — actively misleading to a future reader (including future
Claude Code sessions working from `CLAUDE.md`'s state-machine description).

## Text changes

`de_de.json`:
- `matheaufgabenmod.budget.soft.title`: `"Zeit ist um!"` → `"Gleich ist Schluss!"` (the old title
  asserts time has already run out, which is wrong 5 minutes early).
- `matheaufgabenmod.budget.soft.subtitle`: unchanged — `"Du hast noch 5 Minuten zum Aufräumen."`
  already reads correctly as a pre-warning.
- Lang key names (`budget.soft.*`) are left as-is; only values change. (Renaming the keys to
  `budget.warning.*` would touch the screen class too — deferred as pure churn with no behavior
  change; can be done later if desired.)

`BudgetHardTimeoutScreen` text is unchanged — it still fires at the moment the chosen budget is
used up, which is what its copy already describes.

## HUD (`BudgetHudRenderer`)

`case EXPIRED -> "Schlusszeit: ..."` becomes `case WARNING -> "Schlusszeit: ..."`. Label and red
color (`0xFFFF5555`) unchanged. Because `remainingTicks()` no longer branches on phase, the
displayed countdown now correctly reflects time until the actual, exact end (previously it showed
the grace-period countdown, which was a separate, additional span).

## Test changes

- `BudgetStateTest` / `BudgetTrackerTest`: existing boundary tests move from `budgetTicks` /
  `budgetTicks + GRACE_TICKS` to `budgetTicks - WARNING_TICKS` / `budgetTicks`, and rename
  `EXPIRED` references to `WARNING`.
- New test: budget ≤ `WARNING_TICKS` skips `WARNING` and goes straight from `ACTIVE` to
  `HARD_TIMEOUT` at `budgetTicks` elapsed.
- No new Screen or HUD tests needed (unchanged testing boundary: Screens/HUD stay
  manual-verification-only per project convention).

## Non-goals

- No change to the budget-query screen, presets, or min/max bounds.
- No change to whether math prompts keep firing during `WARNING` (they do, same as they did
  during `EXPIRED` — unchanged interaction with the prompt scheduler).
- No persistence, multiplayer, or config-surface changes.

## Out of scope / follow-ups

- Renaming the `budget.soft.*` lang keys to `budget.warning.*` (cosmetic, deferred).
- Making the 5-minute warning window configurable (currently a hardcoded constant, matching the
  rest of the budget feature's v1 scope).
