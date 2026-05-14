# Matheaufgaben Play-Budget Timer — Design

- **Date:** 2026-05-11
- **Status:** Draft (pending implementation plan)
- **Owner:** Friedrich
- **Supersedes:** MVP non-goal "Quiet hours / time-of-day scheduling". This is a *daily-budget* timer rather than a clock-time gate, so it doesn't fully reverse that decision — but it's adjacent and worth noting.

## Goal

Limit how much Minecraft the kid can play in a single world-loading session. On entering a world, the kid is asked (via an undismissable Screen) how many minutes they want to play. The mod tracks elapsed active-play time, shows the remaining budget in the top-right HUD, and signals when the time is up. A 5-minute grace period after the budget expires allows the kid to reach a save point or wrap up; after grace, an undismissable popup forces a graceful "Save and Quit" via Minecraft's normal shutdown path.

The system layers on top of (does not replace) the existing math-prompt scheduler. Math prompts continue to fire normally throughout play and the 5-minute grace period.

## Primary user

The *parent* configures nothing — there is no configuration for this feature in v1. Defaults are hardcoded. The *kid* interacts with three screens: a budget-query screen (entry), a soft-expiry popup ("Zeit ist um"), and a hard-expiry popup (forced quit). The kid sets their own budget at world-load time, on the honour system — a 6-year-old typing "999" is a parental conversation, not a technical safeguard.

## Non-goals (v1 of timer)

- **Persistence across world reloads or Minecraft restarts** — leaving the world to title and re-entering re-prompts for a fresh budget. The user has explicitly accepted this loophole for the MVP; closing it requires per-day persistence which is a separate iteration.
- **Multiplayer / Realms / LAN** — singleplayer only, matching the MVP scope.
- **Configurable defaults in the JSON config** — the default budget (60 minutes), the grace period (5 minutes), and the "fast answer" threshold (not used by this feature) are hardcoded constants.
- **Custom HUD position or colour** — top-right, white text, no parent customisation.
- **Pause-on-game-pause is honoured automatically** — Minecraft's singleplayer pause is the source of truth (`client.isPaused()`). No bespoke pause logic.
- **Configurable hard-timeout action** — always `MinecraftClient.scheduleStop()` (graceful save & quit). No "kick to title screen" alternative.
- **Sounds, animations, or haptic feedback** — silent and visual-only.
- **Time-of-day scheduling** ("no Minecraft after 8pm") — explicitly excluded; this is a *duration* gate, not a *clock* gate.
- **Per-kid profiles** — single budget per Minecraft installation.
- **Translations beyond German** — `de_de.json` only.

## Architecture overview

```
┌──────────────────────────────────┐
│       ClientTickEvents           │
│        END_CLIENT_TICK           │
└─────────────┬────────────────────┘
              │ each tick
              ▼
┌──────────────────────────────────┐    ┌───────────────────────────────┐
│       BudgetTracker              │───▶│         BudgetSurface         │
│  state machine (5 states),       │    │  (interface — testable seam)  │
│  ticks elapsed,                  │    │  hasWorld, isPaused,          │
│  opens screens at transitions,   │    │  openBudgetQuery(callback),   │
│  notifies HUD of remaining time  │    │  openSoftExpired,             │
└──────────────────────────────────┘    │  openHardTimeout              │
                                        │  setHudRemaining(ticks)       │
                                        └─────────────┬─────────────────┘
                                                      │
                                                      ▼
                                        ┌───────────────────────────────┐
                                        │  MinecraftBudgetSurface       │
                                        │  (production impl, opens      │
                                        │   real Screens, registers     │
                                        │   HudRenderCallback)          │
                                        └───────────────────────────────┘
```

`BudgetTracker` is pure state + transitions, fully unit-testable via a `FakeBudgetSurface`. The Minecraft-coupled bits (real Screens, HUD render hook) live in `MinecraftBudgetSurface`.

### State machine

Five states:

1. **`WAITING_FOR_WORLD`** — initial state on mod load. No world yet. No HUD.
2. **`WAITING_FOR_BUDGET`** — world loaded, `BudgetQueryScreen` is open. No HUD (would distract from the input).
3. **`ACTIVE`** — budget set. Counting down. HUD visible (white).
4. **`EXPIRED`** — budget reached zero. `BudgetSoftExpiredScreen` was shown once and (hopefully) dismissed. Grace timer counting down. HUD visible (red).
5. **`HARD_TIMEOUT`** — grace period elapsed. `BudgetHardTimeoutScreen` is open. Only escape is "Spiel beenden".

Transitions:

```
WAITING_FOR_WORLD ──hasWorld()→true────▶ WAITING_FOR_BUDGET
                                              │
                                              │ user submits N via BudgetQueryScreen
                                              ▼
                                          ACTIVE
                                              │
                                              │ elapsed >= budget_ticks
                                              ▼
                                          EXPIRED (popup shown once)
                                              │
                                              │ elapsed >= budget_ticks + 5_min_ticks
                                              ▼
                                          HARD_TIMEOUT (forced-quit popup)


  any state ─── hasWorld()→false ───▶ WAITING_FOR_WORLD
                (kid left to title)
```

### Tick semantics

`BudgetTracker.onTick(surface)` runs every client tick:

- If `!surface.hasWorld()`: transition to `WAITING_FOR_WORLD` (clears any HUD, closes nothing — the user already left the world). Don't open the budget query yet; wait for the NEXT `hasWorld=true` transition.
- If transitioning `WAITING_FOR_WORLD → WAITING_FOR_BUDGET` (i.e., world just became available): call `surface.openBudgetQuery(this::onBudgetSubmitted)`.
- If `surface.isPaused()`: do not advance the elapsed counter. (This naturally handles math prompts, the game menu, and the budget screens themselves — all of which pause via `shouldPause=true`.)
- Otherwise, increment `elapsedTicks`.
- Check thresholds:
  - In `ACTIVE`, if `elapsedTicks >= budgetTicks`: transition to `EXPIRED` and call `surface.openSoftExpired()`.
  - In `EXPIRED`, if `elapsedTicks >= budgetTicks + GRACE_TICKS`: transition to `HARD_TIMEOUT` and call `surface.openHardTimeout()`.
- Update HUD: `surface.setHudRemaining(remainingTicks, state)`.

### Budget query callback

`BudgetQueryScreen` takes a `Consumer<Integer> onSubmit` at construction. When the user types a valid positive integer and presses Enter (or clicks the "Los geht's!" button), the screen invokes `onSubmit.accept(minutes)` and closes itself.

`BudgetTracker.onBudgetSubmitted(int minutes)` sets `budgetTicks = minutes * TICKS_PER_MINUTE`, transitions to `ACTIVE`, and resumes normal tick processing.

### Defaults and constants

- Default budget field value when the query screen opens: **60 minutes**.
- Grace period: **5 minutes** (`5 * 60 * 20 = 6000` ticks).
- Minimum allowed budget: **1 minute** (rejects 0 and negative; clamps higher inputs are allowed).
- Maximum allowed budget: **1440 minutes (24 hours)** — sanity bound; very-large values aren't useful and risk integer overflow somewhere down the line.

### Screens

#### `BudgetQueryScreen`
- Title (1.5× scale): "Wie lange willst du spielen?"
- Centered: `TextFieldWidget` (pre-filled with `60`, maxLength 4).
- Below: "Minuten" label.
- "Los geht's!" button (and Enter / numpad Enter accept).
- `shouldPause=true`, `shouldCloseOnEsc=false` (kid cannot skip).
- Invalid input (non-integer, ≤0, >1440): clear field, show red "Bitte eine Zahl zwischen 1 und 1440 eingeben" message.

#### `BudgetSoftExpiredScreen`
- Title (1.5× scale): "Zeit ist um!"
- Subtitle: "Du hast noch 5 Minuten zum Aufräumen."
- "OK" button (and Enter / numpad Enter accept). Dismisses to `null` screen → world resumes.
- `shouldPause=true`, `shouldCloseOnEsc=true` (Esc allowed — this is informational).

#### `BudgetHardTimeoutScreen`
- Title (1.5× scale): "Wirklich Schluss für heute!"
- Subtitle: "Drücke „Spiel beenden", dann wird gespeichert und Minecraft beendet."
- "Spiel beenden" button → `MinecraftClient.getInstance().scheduleStop()`.
- No other buttons.
- `shouldPause=true`, `shouldCloseOnEsc=false`.

### HUD overlay

A `HudRenderCallback` registered at mod init. Each frame:

- If state is `ACTIVE`: draw "Restzeit: MM:SS" top-right in white. MM:SS computed from remaining ticks.
- If state is `EXPIRED`: draw "Schlusszeit: M:SS" top-right in red (`0xFFFF5555`). M:SS is the remaining grace, counting down from 5:00.
- If state is `WAITING_*` or `HARD_TIMEOUT`: HUD draws nothing.

Position: 10 pixels from the right edge, 10 pixels from the top. Text rendered with the standard Minecraft text renderer at default scale.

### Interaction with the math-prompt scheduler

The two systems are independent. They both rely on `client.isPaused()` for pause logic and both call into Minecraft via per-tick callbacks. Concretely:

- Math prompts continue to fire on schedule (every `intervalMinutes` of active play) throughout `ACTIVE` and `EXPIRED`. The kid sees them as usual.
- When a math prompt is on screen, `client.isPaused()` returns true → BudgetTracker stops counting. As soon as the kid solves the prompt and `PromptScreen` closes, both timers resume.
- If `BudgetSoftExpiredScreen` is dismissed and a math prompt would normally have fired during it: the math prompt waits one or more ticks until our screen closes (because `surface.openPromptScreen` only runs when no prompt is up, and our budget screens are not prompts). Then the math prompt opens normally.

### Failure modes

- **`MinecraftClient.scheduleStop()` fails** (extremely unlikely) — log warning, leave the screen up. Kid can still alt-F4.
- **HudRenderCallback throws** — caught and logged; HUD missing but timer logic continues.
- **Budget query screen receives nonsense input** — re-prompts on the same screen (clear field + red error message).
- **Player switches dimensions** — does NOT trigger `hasWorld=false`. Tracker stays in `ACTIVE`. Good — switching dimensions is mid-play, not "leaving the world".

## Test strategy

- **`BudgetStateTest`** — pure state machine: empty state, transitions on world enter / submit / threshold crossing / world leave. ~10 tests.
- **`BudgetTrackerTest`** — integration with a `FakeBudgetSurface`: full lifecycle with controlled tick advancing. Tests for: pause stops the timer, math-prompt pause stops the timer, expiry fires soft-expired callback, grace expiry fires hard-timeout callback, leaving the world resets to WAITING. ~8 tests.
- **No tests for the three Screen subclasses or the HUD renderer** — visual + Minecraft-runtime-required. Verified manually via `runClient`.

## Open questions for plan phase

1. **HUD format below 1 minute** — keep MM:SS throughout, or switch to "<1 min"? Decision: MM:SS throughout for consistency.
2. **Budget query screen — pressable Esc, or hard-block?** Decision: hard-block (matches PromptScreen). Otherwise the kid can Esc out and play with no budget.
3. **Should the budget query screen also block while the world is still loading visuals?** In practice, `client.world != null` becomes true before terrain is fully rendered. Opening our screen at that moment is fine — the world is functional, it just looks fuzzy briefly.
4. **What if the kid types `0`?** Reject. Minimum is `1`. Same error path as non-integer input.
5. **HUD's right-edge offset** — 10px is the proposed default; the plan should leave it as a named constant so the parent could later tweak by editing source (no JSON knob in v1).

## Out of scope (deferred to a later iteration)

- **Per-day budget persistence** (closes the quit-and-rejoin loophole). Would need a daily-state file like `<config>/matheaufgabenmod-budget-2026-05-11.json`.
- **Parent-set hard maximum** ("kid cannot enter more than 60 minutes") — would live in the JSON config.
- **Pause-aware math prompts** — currently math prompts continue during grace; could optionally suppress them.
- **Achievements integration** — could grant a "Pünktlich" achievement for finishing the budget without going into grace. Out of scope for v1; this can be added once both features exist.
- **A "Tomorrow's budget" carry-over hint** if the kid stops early. Pure UX flourish.
