# minecraft-matheaufgaben-mod

A Fabric client-side mod for Minecraft Java Edition 1.21.x that interrupts singleplayer
gameplay every X minutes with a math-problem prompt. The kid must answer correctly to
resume play; wrong answers re-prompt with a fresh problem. Parent configures the interval
and the problem types via a JSON config file.

The mod ships four problem types — addition (`plus`), subtraction (`minus`),
multiplication tables (`einmaleins`), and division (`division`) — each with explicit
configurable constraints (range, carry/borrow requirement, factor rows, divisor sets,
optional remainders).

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (one-time).
2. Drop `matheaufgabenmod-0.1.0.jar` and the
   [Fabric API](https://modrinth.com/mod/fabric-api) jar into `<minecraft>/mods/`.
3. Launch Minecraft. The mod creates `<minecraft>/config/matheaufgabenmod.json` with
   defaults on first run.
4. Edit the JSON to taste; restart Minecraft.

## Configuration

`<minecraft>/config/matheaufgabenmod.json`:

```json
{
  "intervalMinutes": 5,
  "sectionSpecs": [
    "plus:range=100,count=1,carry=mixed",
    "minus:range=100,count=1,borrow=mixed",
    "einmaleins:rows=2-9,count=1",
    "division:divisor=2-9,count=1"
  ]
}
```

- `intervalMinutes` — minutes of *active play* between prompts (timer pauses with the world).
- `sectionSpecs` — list of `type:k=v[,k=v...]` strings. Each interruption picks one spec
  uniformly at random and generates one problem from it. Run `./gradlew test` and read
  the per-generator test classes for the supported parameters of each type, or consult
  the `describe()` method on each generator class in source.

## History log

The mod appends every math-task submission to `<minecraft>/config/matheaufgabenmod-history.log` as
fixed-width space-aligned rows with a header. Columns: `timestamp`, `type`, `prompt`, `expected`,
`given`, `result`, `duration_s`. Useful for spotting which problem types or operations the kid
struggles with. The file is append-only and survives Minecraft restarts; delete it manually to
reset the history. For machine reading, split on two or more whitespace characters (e.g.
`awk -F'\s{2,}' ...`) — the prompt's internal single spaces are preserved.

## Play-budget timer

On entering a world, the mod asks for a play-time budget in minutes (1–1440). A HUD in the
top-right shows the remaining time. When the budget runs out, a "Zeit ist um!" popup appears
that can be dismissed; the kid then has a 5-minute grace period (HUD turns red, counting down)
to reach a save point. After grace, a forced-quit popup appears with only a "Spiel beenden"
button — clicking it triggers Minecraft's normal save-and-quit. No data is lost.

The budget pauses automatically whenever the game is paused (game menu, math prompt, the
budget popups themselves). Leaving the world to title and re-entering re-prompts for a fresh
budget — there is no per-day cap in v1.

## Build

```sh
./gradlew build              # compile + test + assemble jar
./gradlew test               # JUnit only
./gradlew runClient          # launch a dev Minecraft instance with the mod loaded
```

Java 21 toolchain required (Loom auto-pulls if absent).

The design spec lives at `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`.
