# Minecraft Matheaufgaben-Mod — Design

- **Date:** 2026-05-10
- **Status:** Approved (brainstorming complete; pending implementation plan)
- **Owner:** Friedrich (`friedrich@cryptosolutions.de`)
- **Sibling project:** `~/werkbank/schulaufgaben-generator` — same problem domain, different platform.

## Goal

A Fabric client-side mod for Minecraft Java Edition 1.21.x that interrupts singleplayer gameplay every X minutes with a math-problem prompt.
The kid must answer correctly to resume play; wrong answers re-prompt with a fresh problem.
The parent configures the interval and the problem types via a JSON config file.

## Primary user

The *parent* (Friedrich) configures the mod once.
The *kid* (the gated player) interacts only via the runtime prompt — they cannot reach the config or disable the mod from inside Minecraft.
The tool is for the kid's own machine, vanilla MC + this mod, no other mods expected.

## Non-goals (v1)

- Multiplayer / Realms / LAN / dedicated-server compatibility — pure singleplayer.
- In-game settings GUI, ModMenu integration, Cloth Config — kid would just disable it.
- Cross-version compatibility (1.20.x, 1.22.x). Pinned to 1.21.4.
- Sodium / Iris / OptiFine compatibility shims.
- Sachaufgaben, units, geometry, multi-step problems — same out-of-scope list as the CLI carries over.
- Per-kid progress tracking, statistics, score history, achievements — stateless prompt loop only.
- Difficulty escalation over time — one consistent profile until the parent edits the config.
- Quiet hours / time-of-day scheduling.
- Hot-reload of config without game restart.
- Translations beyond German (`de_de.json`).
- Skip / dismiss / "I give up" buttons — once the prompt is up, only a correct answer closes it.
- Hint system after N wrong answers.
- Custom font / textures / sound effects.
- Publishing to Modrinth / CurseForge — personal-use mod.

Each is a YAGNI cut.
Naming them prevents drift.

## Architecture overview

```
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  fabric.mod.json │    │  ConfigLoader    │    │ PromptScheduler  │    │   PromptScreen   │
│ (entrypoint:     │───▶│ reads JSON,      │───▶│ ClientTickEvents │───▶│ Screen subclass; │
│  ModInitializer) │    │ parses sections, │    │ counts active    │    │ TextFieldWidget; │
│                  │    │ holds ModConfig  │    │ ticks; opens     │    │ pauses world in  │
│                  │    │                  │    │ PromptScreen     │    │ SP via setScreen │
└──────────────────┘    └──────────────────┘    └──────────────────┘    └──────────────────┘
                                                          │                       │
                                                          │                       ▼
                                                          │              ┌──────────────────┐
                                                          └─────────────▶│ Generator        │
                                                                         │ Registry +       │
                                                                         │ pure functions   │
                                                                         │ (rng, params)    │
                                                                         │ → Problem        │
                                                                         └──────────────────┘
```

### The four hard seams

1. **ModInitializer ↔ everything else.**
   `MatheaufgabenMod` runs once at client startup: loads config, builds the registry, registers `ClientTickEvents.END_CLIENT_TICK` with `PromptScheduler`. After that it does nothing.
2. **Generators ↔ Screen.**
   Generators are pure: `(Random rng, Object params) → Problem`. They have no Minecraft imports, no UI knowledge.
   Unit-testable in plain JUnit without launching the game.
   The Screen receives a fully-formed `Problem` and renders it.
3. **PromptScheduler ↔ PromptScreen.**
   Scheduler decides *when* to prompt; Screen decides *what* the prompt looks like.
   Scheduler picks one section spec at random from `ModConfig.sectionSpecs`, asks the registry for the generator, calls `generate`, hands the resulting `Problem` to the Screen.
4. **Config ↔ runtime.**
   Config is loaded once at startup.
   No hot-reload in v1.
   If the parent edits the file, restart Minecraft.

### Data flow per interruption

```
client tick (50ms) ──▶ scheduler.tick():
                         if world == null || client.isPaused(): skip
                         elapsedTicks += 1
                         if elapsedTicks >= config.intervalMinutes * 60 * 20:
                             elapsedTicks = 0
                             pickedSpec = rng.choice(config.sectionSpecs)
                             gen = registry.get(pickedSpec.type)
                             problem = gen.generate(rng, pickedSpec.params)
                             client.setScreen(new PromptScreen(problem, ...))
```

When the kid types an answer + Enter:

- Correct: `client.setScreen(null)` resumes play; scheduler timer is already at 0, starts counting again on next tick.
- Wrong: the screen calls the supplied `Supplier<Problem>` for a fresh pick (same registry pool, possibly different type), replaces `this.problem`, clears input, redraws.

### Invariants

- No prompt ever fires on the title screen (no `client.world`).
- Pausing the game pauses the timer — kid can't game the system by leaving the menu open with the world running. (In SP, opening the inventory or pause menu pauses the game, so this is automatic.)
- One active `PromptScreen` at a time — scheduler checks `client.currentScreen instanceof PromptScreen` before opening a new one.

## Configuration model

### File location and format

```
<minecraft_root>/config/matheaufgabenmod.json
```

JSON via Gson (already bundled with Minecraft — no extra dependency).
Created with sane defaults the first time the mod runs if the file is absent.
Schema:

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

### Fields

| Field | Type | Default | Meaning |
|---|---|---|---|
| `intervalMinutes` | int (>= 1) | 5 | Minutes of *active play* (timer pauses with the world) between prompts. |
| `sectionSpecs` | `list[string]` | the four-line block above | Each entry is a `type:k=v,k=v` spec using the **identical grammar** to the CLI. Each interruption picks one spec uniformly at random, generates **one** problem from it. The spec's `count` param is accepted (so existing CLI configs paste cleanly) but ignored at runtime — every prompt is one problem. |

That's it for v1.
Two fields.
If a parent wants per-day variation, they edit the file.

### Section-spec grammar (verbatim from the CLI)

```
<type>:<k>=<v>[,<k>=<v>...]
```

- Same four registered types: `plus`, `minus`, `einmaleins`, `division`.
- Same parameter names and shapes as the CLI's `parse_params`. E.g. `plus:range=100,carry=yes`, `einmaleins:rows=2-9`, `division:divisor=2-9,with_remainder=true,result_max=10`.
- Parser is a port of `parse_section_spec` from `cli.py` — same rejection rules (missing colon → error, malformed `k=v` → error, duplicate keys → error).

### Validation and error policy

Config errors surface at mod init time:

- File exists but malformed JSON → log error to Minecraft's `[ERROR]` log channel, fall back to defaults, continue. Mod still works.
- File parses but a `sectionSpec` is invalid → log a warning naming the bad spec, drop it from the list, continue with the rest.
- Empty `sectionSpecs` after validation → log error, fall back to default specs. Mod is never disabled by bad config.

Rationale: a child whose math interrupts stop working because of a config typo is a worse outcome than running with sensible defaults.

### Where defaults come from

A constant `ModConfig.DEFAULT` in `config/ModConfig.java` defines the four-spec default block above.
`ConfigLoader` writes it to disk on first run.
Gson does not support comments, so the explanation of each field lives in the README and in code.

### What is *not* configurable in v1

- **Klasse hint** — the prompt screen shows one prompt at a time; no PDF/columns/font-size decisions like the CLI.
- **Wrong-answer policy** — hardcoded to "re-prompt with new problem" (random spec re-pick).
- **UI strings / language** — German only via the lang file; not config-overridable.
- **Multiple difficulty profiles** — there is one set of section specs that applies always.
- **Quiet hours / time-of-day rules.**
- **Maximum attempts / lockout / penalty escalation.**

## Generator port

### What gets ported, what gets stripped

The four Python generators (`plus.py`, `minus.py`, `einmaleins.py`, `division.py` in the CLI) carry over to Java with semantic parity for problem generation:

- Same prompt format strings (`"a + b"`, `"a − b"` with U+2212, `"a · b"` with U+00B7, `"a : b"` with ASCII colon).
- Same answer format (`"42"`, `"3 R 1"` for division-with-remainder).
- Same parameter names, ranges, and validation order (unknown → missing → type → value → capacity).
- Same exact-enumeration `_CARRY_CAPACITY` / `_BORROW_CAPACITY` precomputation for the fail-fast contract.
- Same uniqueness-within-a-batch invariant — irrelevant in the mod since each prompt is one problem, but kept so test suites mirror the CLI's.

What gets **stripped**:

- The `metadata` dict on each `Problem` — the mod doesn't render footers or use it. `Problem` is just `(prompt, answer)`.
- Section titles — the prompt UI shows the prompt as-is, no German section heading.
- The Python `register()` self-registration trick. Java's static initialisers can do the same, but a single `Registry` class with explicit construction is cleaner in Java and avoids `<clinit>` order surprises.

### `Generator` interface

```java
package dev.asante.matheaufgabenmod.generator;

import java.util.List;
import java.util.Map;
import java.util.Random;

public interface Generator {
    String name();                                      // e.g. "plus"
    Object parseParams(Map<String, String> raw);        // returns generator-specific Params record
    List<Problem> generate(Random rng, Object params);  // matches CLI; mod calls .get(0)
    String describe();                                  // for log messages on bad config
}
```

`generate` returns `List<Problem>` to mirror the CLI port and keep the test suites portable verbatim.
The mod calls `.get(0)` on each scheduler tick — one prompt = one problem.

`Object` for params (rather than a generic `Generator<P>`) because the registry stores heterogeneous generators by name — the same Protocol-contravariance pragma we used in the Python port.
Each generator narrows internally with an `instanceof` check at the top of `generate`.

### Per-generator parameter records

Each generator owns a small immutable record:

```java
public record PlusParams(int range, int count, CarryMode carry) {}
public record MinusParams(int range, int count, BorrowMode borrow, boolean negativeResults) {}
public record EinmaleinsParams(int[] rows, int count) {}
public record DivisionParams(int[] divisors, int count, boolean withRemainder, int resultMax) {}
```

`parseParams` for each is a port of the Python `parse_params`, including the same error messages prefixed with the generator name.
Validation throws a project-wide `ConfigException` (a runtime exception); `ConfigLoader` catches it.

### Generation algorithms

Direct ports:

- **plus**: rejection sampling with `seen` set; `_hasCarry(a, b)` walks digits; `_carryMatches(a, b, mode)` filters; `max(1000, 200 * count)` retry budget; raises if budget exhausted before reaching `count`.
- **minus**: same shape, with `_hasBorrow` digit walk; `negativeResults` toggles whether `b ≤ a` is enforced.
- **einmaleins**: build `allPairs` via nested loops over `rows × {1..10}`, `Collections.shuffle` with the seeded RNG, take first `count`.
- **division**: build `allTriples` via `divisor × resultMax × (with_remainder ? d : 1)`, shuffle, compute dividend = `d*q + r`.

### Capacity precomputation

`PlusGenerator` and `MinusGenerator` compute their capacity tables in `static` blocks (Python's import-time precompute → Java's `static { ... }`):

```java
private static final Map<CarryKey, Integer> CARRY_CAPACITY = computeCarryCapacities();

private record CarryKey(int range, CarryMode mode) {}

private static Map<CarryKey, Integer> computeCarryCapacities() {
    Map<CarryKey, Integer> out = new HashMap<>();
    for (int r : VALID_RANGES) {
        int yes = 0, no = 0;
        for (int a = 0; a <= r; a++) {
            for (int b = 0; b <= r - a; b++) {
                if (hasCarry(a, b)) yes++; else no++;
            }
        }
        out.put(new CarryKey(r, CarryMode.YES), yes);
        out.put(new CarryKey(r, CarryMode.NO), no);
    }
    return out;
}
```

One-time cost paid at mod load.
Same fail-fast contract as the CLI: `parseParams` rejects impossible counts before `generate` runs.

### Determinism

`PromptScheduler` holds one `Random` instance (default-seeded at scheduler construction) and threads it through every problem-pick.
The instance's state advances across calls, so successive prompts get different problems even when the same section spec is picked twice in a row.
The mod is **not deterministic across runs** the way the CLI is — there's no `--seed` for parents to reproduce a sheet.
That is intentional: the mod's job is variety, not reproducibility.

## Prompt UI, scheduling, wrong-answer flow

### `PromptScheduler`

A `ClientTickEvents.END_CLIENT_TICK` listener registered once at mod init.
State: a single `int elapsedTicks` counter.

```java
public final class PromptScheduler implements ClientTickEvents.EndTick {
    private final ModConfig config;
    private final Random rng = new Random();
    private int elapsedTicks = 0;

    public void onEndTick(MinecraftClient client) {
        if (client.world == null) { elapsedTicks = 0; return; }   // title screen
        if (client.isPaused()) return;                            // paused (incl. while our screen is up)
        if (client.currentScreen instanceof PromptScreen) return; // belt + suspenders
        elapsedTicks++;
        int threshold = config.intervalMinutes() * 60 * 20;       // ticks
        if (elapsedTicks >= threshold) {
            elapsedTicks = 0;
            openPrompt(client);
        }
    }

    private void openPrompt(MinecraftClient client) {
        Problem problem = pickProblem();
        client.setScreen(new PromptScreen(this::pickProblem, problem));
    }

    private Problem pickProblem() {
        String specStr = config.sectionSpecs().get(rng.nextInt(config.sectionSpecs().size()));
        SectionSpec spec = SectionSpec.parse(specStr);
        Generator gen = Registry.get(spec.type());
        Object params = gen.parseParams(spec.params());
        return gen.generate(rng, params).get(0);                  // generate one
    }
}
```

`pickProblem` is passed into `PromptScreen` as a `Supplier<Problem>` so the screen can request a fresh problem on each wrong answer without coupling to the scheduler's internals.

### `PromptScreen`

Subclasses Minecraft's `Screen`.
In singleplayer, `client.setScreen(...)` automatically pauses the world — that's the whole point of using a Screen rather than a HUD overlay.

Layout (centred, single column):

```
┌─────────────────────────────────────────┐
│                                         │
│           Mathe-Aufgabe!                │  <- title (1.5x text scale)
│                                         │
│             23 + 45 =                   │  <- prompt (2x scale, centred)
│                                         │
│              [____]                     │  <- TextFieldWidget, numeric input
│                                         │
│           [ Antworten ]                 │  <- submit button
│                                         │
│  (on wrong: "Falsch — versuch's         │  <- feedback line (red), only after first
│   nochmal" appears here)                │     submit; cleared on each new problem
│                                         │
└─────────────────────────────────────────┘
```

Components:

- **Title**: `Text.translatable("matheaufgabenmod.prompt.title")` → "Mathe-Aufgabe!" via the German lang file.
- **Prompt text**: rendered with `DrawContext.drawCenteredTextWithShadow`, scaled up 2× via `MatrixStack.scale`.
- **Input field**: `TextFieldWidget`, width ~80px, centred. Accepts digits, minus sign, space, and the letter `R` (for division-with-remainder answers like `"3 R 1"`). Auto-focused on screen open.
- **Submit button**: `ButtonWidget` labelled `Text.translatable("matheaufgabenmod.prompt.submit")` → "Antworten". Activated on Enter as well via `keyPressed` override.
- **Feedback line**: empty initially; on wrong answer set to `Text.translatable("matheaufgabenmod.prompt.wrong")` rendered in red (`0xFFFF5555`). Cleared when next problem is shown.

### Behaviour overrides

```java
@Override public boolean shouldPause() { return true; }           // SP auto-pause
@Override public boolean shouldCloseOnEsc() { return false; }     // can't escape
@Override public void closeOnEnterAccept() { /* no-op */ }        // Enter goes to submit only
```

`shouldCloseOnEsc() = false` is the key bit — without it the kid pressing `Esc` would close the screen and resume play unchallenged.
There is no key sequence that closes a `PromptScreen` other than submitting a correct answer.

### Submit flow

```java
private void onSubmit() {
    String guess = inputField.getText().trim();
    if (guess.equals(currentProblem.answer())) {
        client.setScreen(null);                                   // resume play
    } else {
        currentProblem = problemSupplier.get();                   // fresh problem, same registry pool
        inputField.setText("");
        feedback = Text.translatable("matheaufgabenmod.prompt.wrong");
    }
}
```

Notes:

- Whitespace trim handles `"3 R 1"` with stray spaces.
- The new wrong-answer problem is **not necessarily of the same type** as the original — it's another random pick from `sectionSpecs`. Rationale: avoids a frustration loop where the kid is hammered repeatedly on their weakest type.
- Empty submit is treated as wrong (no special-case shortcut).

### Edge cases handled

- **Window loses focus mid-prompt** → no special handling. Minecraft pauses on focus loss in SP anyway; the screen reappears when the kid clicks back in. Timer stays at 0.
- **Kid alt-tabs out and quits** → world saves and unloads, `client.world` becomes `null`, scheduler resets `elapsedTicks` to 0. Next world join starts the timer fresh.
- **Kid leaves the prompt up indefinitely** → fine. Game is paused, no prompts stack, scheduler is dormant. They can leave it up while making dinner, no penalty.
- **`/reload` or datapack reload** → no impact; mod state is per-client, not per-world.

## Project layout, build, distribution, testing

### Repository layout

```
~/werkbank/minecraft-matheaufgaben-mod/
├── build.gradle                          # Fabric Loom plugin, deps, Java 21 toolchain
├── gradle.properties                     # MC version, loader version, fabric API version
├── settings.gradle                       # rootProject.name = 'matheaufgabenmod'
├── gradle/wrapper/                       # gradle wrapper jars
├── gradlew, gradlew.bat                  # wrapper scripts
├── .gitignore
├── README.md
├── CLAUDE.md
├── docs/superpowers/
│   ├── specs/2026-05-10-minecraft-matheaufgaben-mod-design.md
│   └── plans/2026-05-10-minecraft-matheaufgaben-mod.md
├── src/main/java/dev/asante/matheaufgabenmod/
│   ├── MatheaufgabenMod.java             # ModInitializer entry point
│   ├── config/
│   │   ├── ModConfig.java                # record holding intervalMinutes + sectionSpecs
│   │   ├── ConfigLoader.java             # JSON load/save via Gson, defaults, error policy
│   │   └── SectionSpec.java              # parsed type:k=v,k=v with parse() static method
│   ├── generator/
│   │   ├── Problem.java                  # record (String prompt, String answer)
│   │   ├── Generator.java                # interface
│   │   ├── Registry.java                 # static map of name -> Generator
│   │   ├── PlusGenerator.java            # + PlusParams + CARRY_CAPACITY static block
│   │   ├── MinusGenerator.java           # + MinusParams + BORROW_CAPACITY
│   │   ├── EinmaleinsGenerator.java      # + EinmaleinsParams
│   │   └── DivisionGenerator.java        # + DivisionParams
│   ├── timer/
│   │   └── PromptScheduler.java          # ClientTickEvents listener
│   └── screen/
│       └── PromptScreen.java             # Screen subclass
├── src/main/resources/
│   ├── fabric.mod.json                   # mod metadata, entrypoints, environment=client
│   ├── matheaufgabenmod.mixins.json      # empty for v1 (no mixins needed)
│   └── assets/matheaufgabenmod/
│       └── lang/
│           └── de_de.json                # German UI strings (title, submit, wrong)
└── src/test/java/dev/asante/matheaufgabenmod/
    ├── generator/
    │   ├── PlusGeneratorTest.java        # JUnit 5; ports the schulaufgaben test suite
    │   ├── MinusGeneratorTest.java
    │   ├── EinmaleinsGeneratorTest.java
    │   └── DivisionGeneratorTest.java
    └── config/
        └── SectionSpecTest.java          # parse_section_spec port
```

The package namespace `dev.asante.matheaufgabenmod` mirrors the `asante.dev` site.

### Build

- **Fabric Loom** Gradle plugin (the standard build tool for Fabric mods).
- **Java 21 toolchain** — Minecraft 1.21.x requires Java 21 at runtime. Gradle's toolchain support pulls the right JDK automatically.
- **Dependencies**: `net.fabricmc:fabric-loader`, `net.fabricmc.fabric-api:fabric-api`, `com.google.code.gson:gson` (already on the Minecraft classpath; declared `implementation` only for tests). JUnit 5 (`org.junit.jupiter:junit-jupiter`) for tests.
- **Versions** pinned in `gradle.properties`:
  - `minecraft_version=1.21.4` (current stable at the time of writing)
  - `loader_version=0.16.x` (latest Fabric loader)
  - `fabric_version=0.110.x+1.21.4` (Fabric API for that MC version)
  - `mod_version=0.1.0`

The plan will fix exact patch versions at scaffolding time after consulting [fabricmc.net/develop](https://fabricmc.net/develop/) for current numbers.

### Common Gradle commands

```sh
./gradlew build                     # compile + test + assemble jar
./gradlew test                      # JUnit only (fast)
./gradlew runClient                 # launch a dev Minecraft instance with the mod loaded
./gradlew genSources                # decompile MC sources for IDE navigation
```

### Distribution

A single `build/libs/matheaufgabenmod-0.1.0.jar`.
To install on the kid's machine:

1. Install the Fabric loader (one-time, from [fabricmc.net](https://fabricmc.net/use/installer/)).
2. Drop the mod jar plus the Fabric API jar into `<minecraft>/mods/`.
3. Launch — the mod creates `<minecraft>/config/matheaufgabenmod.json` with defaults.
4. Edit the JSON to taste; restart Minecraft.

No publishing to Modrinth/CurseForge for v1.

### Testing strategy

**Unit tests (JUnit 5, no Minecraft runtime needed):**

- **Per-generator** (`PlusGeneratorTest` etc.) — port the schulaufgaben-generator test suites verbatim:
  - Count exactness (`generate(rng, params).size() == count`).
  - Range/carry/borrow/result_max compliance — re-derive constraints from the prompt string.
  - Correctness — for each `Problem`, parse the prompt and recompute the expected answer.
  - Uniqueness within a batch.
  - Determinism — same `Random(seed)` → same `List<Problem>`.
  - Capacity-overrun raises in `parseParams` (the precomputed `CARRY_CAPACITY` / `BORROW_CAPACITY` regression).
  - Parameter validation — unknown / missing / invalid values throw `ConfigException`.

- **Section spec parser** (`SectionSpecTest`) — port `tests/test_cli.py`'s parser tests. Same grammar, same rejection rules.

**Integration tests:**

- **`PromptSchedulerTest`** — instantiate the scheduler with relevant fields injected; fake the tick loop; assert prompt opens after the configured interval, doesn't open while paused, doesn't double-stack.
- **`PromptScreenTest`** — limited; Minecraft's `Screen` machinery is hard to mock cleanly. Cover what we can: submit logic, wrong-answer transition, Enter handling. Anything that requires a live `MinecraftClient` is verified manually via `./gradlew runClient`.

**Manual smoke** (one-time, after build):

- `./gradlew runClient` → create a singleplayer world → set `intervalMinutes=1` in config → wait 60 seconds → confirm prompt opens, world freezes, correct answer resumes play, wrong answer re-prompts.
