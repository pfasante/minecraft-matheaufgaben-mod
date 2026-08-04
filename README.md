# minecraft-matheaufgaben-mod

A Fabric client-side mod for **Minecraft Java Edition 26.1.2** that interrupts singleplayer
gameplay every X minutes with a math-problem prompt. The kid must answer correctly to
resume play; wrong answers re-prompt with a fresh problem. The parent configures the
interval and the problem types via a JSON config file. A separate play-budget timer caps
total daily play time with graceful save & quit at expiry.

The mod ships four problem types — addition (`plus`), subtraction (`minus`),
multiplication tables (`einmaleins`), and division (`division`) — each with explicit
configurable constraints (range, carry/borrow requirement, factor rows, divisor sets,
optional remainders).

## Install (the easy way: deploy archive)

For Windows installs, see the prebuilt **`matheaufgabenmod-windows-deploy.zip`** at the
repo root — it bundles the Fabric installer, the matching Fabric API jar, the mod jar,
and a step-by-step `INSTALL.md`. Drop the zip on the kid's machine, unzip, follow the
instructions. ~5 minutes total.

## Install (manual)

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and select **MC 26.1.2**
   during the install. Java 25 is required at runtime; the Mojang Launcher will download
   its own bundled JRE 25 automatically — no separate Java install needed.
2. Launch the Fabric profile once to create `<minecraft>/mods/`. Quit.
3. Drop these two files into `<minecraft>/mods/`:
   - **`fabric-api-0.149.0+26.1.2.jar`** (the Fabric API runtime library)
   - **`matheaufgabenmod-0.1.0.jar`** (built from this repo via `./gradlew build`)
4. Launch Minecraft. On entering a world, a budget-query screen appears with three
   preset buttons (30 min, 60 min, or "Eigene Zeit…" for a custom value).
5. The mod creates `<minecraft>/config/matheaufgabenmod.json` with defaults on first
   run. Edit to taste; restart Minecraft.

## Configuration

`<minecraft>/config/matheaufgabenmod.json`:

```json
{
  "intervalMinutes": 5,
  "tasksPerIteration": 5,
  "sectionSpecs": [
    "plus:range=100,count=1,carry=mixed",
    "minus:range=100,count=1,borrow=mixed",
    "einmaleins:rows=2-9,count=1",
    "division:divisor=2-9,count=1"
  ]
}
```

- **`intervalMinutes`** — minutes of *active play* between prompts (timer pauses with the
  world; math prompts and the budget popups all count as paused).
- **`tasksPerIteration`** — how many problems the kid must solve in a row when a prompt
  fires before play resumes (default 1). Each task picks a random section spec, so a
  3-task session might mix plus + division + minus. The screen shows "Aufgabe X von Y"
  as progress.
- **`sectionSpecs`** — list of `type:k=v[,k=v...]` strings. Each task picks one spec
  uniformly at random and generates one problem from it. Run `./gradlew test` and read
  the per-generator test classes for the supported parameters of each type, or consult
  the `describe()` method on each generator class in source.

## Play-budget timer

On entering a world, the mod asks for a play-time budget. A query screen offers three
options:

- **30 Minuten** — one click, plays for 30 minutes.
- **60 Minuten** — one click, plays for 60 minutes.
- **Eigene Zeit…** — reveals a text field for any value from 1 to 1440 minutes.

A HUD in the top-right shows the remaining time as `Restzeit: MM:SS` in white. Five minutes
before the budget runs out, a "Gleich ist Schluss!" popup appears that can be dismissed as a
heads-up to wrap up (HUD turns red and counts down `Schlusszeit: M:SS`); budgets of 5 minutes
or less skip this warning. When the chosen budget is fully used up, a forced-quit popup appears
with only a "Spiel beenden" button — clicking it triggers Minecraft's normal save-and-quit. No
data is lost. Total playtime always equals the chosen budget exactly.

The budget pauses automatically whenever the game is paused (game menu, math prompt, the
budget popups themselves). Leaving the world to title and re-entering re-prompts for a
fresh budget — there is no per-day cap in v1.

## History log

Every math-task submission is appended to `<minecraft>/config/matheaufgabenmod-history.log`
as a fixed-width space-aligned row. Columns:

| Column | Width | Description |
| --- | --- | --- |
| `timestamp` | 19 | `yyyy-MM-dd HH:mm:ss` |
| `player` | 16 | Mojang username at attempt time (distinguishes shared-machine accounts) |
| `type` | 10 | One of `plus`, `minus`, `einmaleins`, `division`, `unknown` |
| `prompt` | 13 | e.g. `42 + 17` |
| `expected` | 9 | The canonical answer |
| `given` | 9 | What the kid typed (whitespace-trimmed) |
| `result` | 7 | `correct` or `wrong` |
| `duration_s` | — | Seconds with 2 decimals, time-to-answer for this attempt |

Append-only, survives Minecraft restarts. Useful for spotting which problem types or
operations the kid struggles with. Delete the file manually to reset. For machine
reading, split on two or more whitespace characters (e.g. `awk -F'\s{2,}' ...`) — the
prompt's internal single spaces are preserved.

## Build

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build              # compile + test + assemble jar
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test               # JUnit only
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew runClient          # launch a dev Minecraft instance
```

**Java 25 is required** (Minecraft 26.x runtime requirement). On Fedora:
`sudo dnf install java-25-openjdk-devel`. Set `JAVA_HOME` explicitly because the system
default `java` may be something else.

Gradle 9.4.0 wrapper, Loom 1.16 (`net.fabricmc.fabric-loom` namespace), Fabric Loader
0.19.x, Fabric API `0.149.0+26.1.2`. No mappings layer — MC 26.x ships with official
names baked into the jar.

The design spec lives at `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`.
Architecture notes for ongoing work are in `CLAUDE.md`.
