# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Fabric client-side mod for Minecraft Java Edition 1.21.x. Interrupts singleplayer
gameplay every X minutes with a math prompt; the kid must answer correctly to resume.
Four problem types (plus, minus, einmaleins, division), each parameterised through a
`type:k=v[,k=v...]` section spec in the JSON config.

The full design and the YAGNI list are in `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md` — read that before proposing scope expansions.

## Common commands

```sh
./gradlew build                           # compile + test + assemble jar
./gradlew test                            # JUnit only (fast)
./gradlew test --tests PlusGeneratorTest  # one suite
./gradlew runClient                       # launch dev Minecraft instance
./gradlew genSources                      # decompile MC sources for IDE navigation
```

## Architecture (the seams that matter)

- **`generator/`** — pure functions, no Minecraft imports. `Generator` interface; one
  implementation per problem type (`PlusGenerator`, `MinusGenerator`, `EinmaleinsGenerator`,
  `DivisionGenerator`). `Registry` exposes them by name. Each `parseParams` rejects
  impossible counts before `generate` runs (the fail-fast contract); `PlusGenerator`
  and `MinusGenerator` precompute exact-capacity tables in static blocks because closed-
  form heuristics overestimate for small ranges and would let `generate` blow up at
  runtime.
- **`config/`** — `SectionSpec.parse` parses the `type:k=v[,k=v...]` grammar with
  descriptive `ConfigException` messages on every malformation. `ModConfig` is a frozen
  record with `intervalMinutes` + `sectionSpecs`. `ConfigLoader` reads JSON via Gson,
  falls back to defaults on any error rather than disabling the mod (a child whose
  prompts stop firing because of a config typo is a worse outcome than running with
  defaults).
- **`timer/`** — `PromptScheduler` is unit-testable: it depends on a narrow
  `ClientSurface` interface (`hasWorld`, `isPaused`, `currentScreenIsPrompt`,
  `openPromptScreen`) rather than `MinecraftClient` directly. `MinecraftClientSurface`
  is the production implementation. The scheduler counts only active-play ticks (paused
  game freezes the timer automatically because `client.isPaused()` returns true while
  any Screen — including ours — is open).
- **`screen/`** — `PromptScreen` extends Minecraft's `Screen`. `shouldPause()` returns
  true so SP auto-pauses the world; `shouldCloseOnEsc()` returns false so the kid can't
  Esc-bail. `checkAnswer` is a static helper extracted so it can be unit-tested without
  booting Minecraft.
- **`history/`** — `HistoryLogger` appends one fixed-width row per math-task submission to
  `<minecraft>/config/matheaufgabenmod-history.log` (column widths chosen for human reading;
  2-space separators between fields). `HistoryEntry.fromAttempt` derives the generator type
  by scanning the prompt for the operator character (avoiding an invasive `type` field on
  `Problem`). IOException-tolerant: a failed log goes to SLF4J `warn` and is swallowed so
  the prompt flow never crashes on a disk error.
- **`budget/`** — `BudgetTracker` runs a 5-state machine (WAITING_FOR_WORLD → WAITING_FOR_BUDGET
  → ACTIVE → EXPIRED → HARD_TIMEOUT) ticked from `ClientTickEvents.END_CLIENT_TICK`. The
  `BudgetSurface` interface is the test seam (same pattern as `ClientSurface` in `timer/`).
  Three Screen subclasses (`BudgetQueryScreen`, `BudgetSoftExpiredScreen`,
  `BudgetHardTimeoutScreen`) handle entry, soft expiry, and hard expiry; the last
  has only a "Spiel beenden" button calling `MinecraftClient.scheduleStop()` for graceful
  save & quit. `BudgetHudRenderer` registers a `HudRenderCallback` for the top-right
  Restzeit/Schlusszeit overlay. State is session-local — leaving the world resets it.
- **`MatheaufgabenMod.java`** — the only `ClientModInitializer`. Loads config, builds
  the scheduler with one shared `Random`, registers a `ClientTickEvents.END_CLIENT_TICK`
  listener.

The hard rule: **generators own answer correctness; the screen renders; the scheduler
schedules. Tests exist at every seam except the `Screen` rendering path.** Anything
that requires a live `MinecraftClient` is verified manually via `./gradlew runClient`.

## Testing conventions

- One test class per generator (`PlusGeneratorTest` etc.). Each suite covers count
  exactness, constraint compliance (range / carry / borrow / result_max / divisor set),
  correctness (re-derive the answer from the parsed prompt and compare), uniqueness
  within a batch, determinism (same `Random(seed)` produces the same `List<Problem>`),
  capacity-overrun raises in `parseParams`, and parameter validation (unknown / missing
  / out-of-range values throw `ConfigException`).
- The scheduler is tested via `FakeClient` (a `ClientSurface` test double) — never
  against the real Minecraft runtime.
- The Screen has unit tests for the `checkAnswer` helper only; layout / rendering /
  Enter-key handling is verified manually via `runClient`.

## Adding a new problem type

1. Create `generator/<Name>Generator.java` implementing `Generator`. Mirror the
   structure of `PlusGenerator` (validation, params record, capacity, generate).
2. Add `registerInto(map, new <Name>Generator())` to the `Registry` static block.
3. Write `test/<Name>GeneratorTest.java` mirroring an existing test class.
4. `./gradlew test`.

## What is *not* in scope for v1

Multiplayer, in-game GUI / ModMenu, hot-reload, translations beyond German, Sodium/Iris
shims, difficulty escalation, quiet hours, hint system, skip buttons, custom textures,
Modrinth/CurseForge publishing. Adding any of these is a spec discussion. The full list
lives in the design spec under "Non-goals (v1)".

## Post-MVP TODOs

These features are out of scope for v1 (per the design spec's "Non-goals" list) but the project owner wants them tracked for future iterations:

- [x] ~~Logging feature: log each math task solved or failed, including timestamp.~~ Shipped: see `history/` package and the "History log" README section.
- [x] ~~Configurable play-budget timer: allow Minecraft to be played with normal math-task settings for X minutes (configurable) before any math tasks kick in.~~ Shipped: see `budget/` package and the "Play-budget timer" README section.
- [ ] Time-limited prompts: after the initial X-minute play budget, math tasks are time-limited and must be solved within Y seconds; on timeout, generate a new task and shorten the interval between new tasks.
- [x] ~~Configurable tasks-per-iteration: make the number of math tasks that must be completed in each prompt interruption configurable (default to 1).~~ Shipped: top-level `tasksPerIteration` JSON config option. `PromptScreen` chains N problems from `Supplier<Problem>` (each picks a random section spec for type variety) before closing; shows "Aufgabe X von Y" progress label when N > 1.
- [x] ~~Budget-chooser presets: rework `BudgetQueryScreen` to offer two one-click preset buttons (30 min, 60 min) plus a third "Eigene Zeit…" option that reveals the existing text-input field.~~ Shipped: `BudgetQueryScreen` now starts in preset mode with three stacked buttons; "Eigene Zeit…" toggles `customMode` and `rebuildWidgets()` swaps to the text-input flow.
- [x] ~~Player name in history log: add a `player` column to `HistoryEntry`.~~ Shipped: `HistoryEntry` carries a `player` field; `HistoryLogger` outputs a 16-char column between `timestamp` and `type`. Production wiring reads `Minecraft.getInstance().getUser().getName()` in `MinecraftClientSurface.openPromptScreen`.
- [x] ~~Change default budget from 60 min → 30 min.~~ Shipped: `DEFAULT_CUSTOM_MINUTES` in `BudgetQueryScreen` is now 30 (used as the prefilled value when the user picks "Eigene Zeit…"). The preset buttons themselves are 30 and 60.
- [ ] Re-target MC 26.x once Mojang publishes `client_mappings` for it (likely days/weeks after 26.x release). Steps when ready: edit `gradle.properties` `minecraft_version=26.1.x`, edit `fabric_version=0.149.0+26.1.x` (or current), rebuild against Mojmap, fix any 1.21.11 → 26.x API breakages surfaced by the compile, widen `fabric.mod.json` `depends.minecraft` to `>=1.21.4 <27`. Should be a sub-hour port given Mojmap is already wired in.
