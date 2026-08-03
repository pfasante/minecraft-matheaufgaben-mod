# Budget Warning Before Expiry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the play-budget "5 Minuten" warning from firing *after* the chosen budget expires (currently `budget + 5min` total playtime) to firing 5 minutes *before* it expires, so total playtime equals exactly the chosen budget.

**Architecture:** Reuse the existing `EXPIRED` phase slot in `BudgetTracker`'s state machine, renaming it `WARNING` and moving its trigger point from `elapsedTicks >= budgetTicks` to `elapsedTicks >= budgetTicks - WARNING_TICKS`. `HARD_TIMEOUT` moves from `budgetTicks + GRACE_TICKS` to exactly `budgetTicks`. Budgets ≤ 5 minutes skip the `WARNING` phase entirely (no room for a warning window) and go straight from `ACTIVE` to `HARD_TIMEOUT`.

**Tech Stack:** Java 25, Fabric/Loom, JUnit 5, Gson (unaffected by this change).

## Global Constraints

- All gradle/test commands need `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` (see project `CLAUDE.md`).
- No JSON config surface changes — the 5-minute window stays a hardcoded constant, matching the rest of the budget feature's v1 scope (per spec's "Non-goals").
- No changes to `BudgetQueryScreen`, presets, or min/max bounds (1–1440 minutes).
- Lang key *names* stay as-is (`matheaufgabenmod.budget.soft.*`); only the title *value* changes. Renaming the keys is out of scope (see spec's "Out of scope").
- Spec: `docs/superpowers/specs/2026-08-03-budget-warning-before-expiry-design.md`.

---

## Task 1: Shift the state-machine trigger point (`BudgetState`/`BudgetPhase`)

**Files:**
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetPhase.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetState.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java:40` (mechanical: the enum constant it references is being renamed in this task, so this reference must be updated in the same commit to keep the module compiling — the method-name rename (`openSoftExpired` → `openWarning`) is deliberately deferred to Task 2)
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHudRenderer.java:47` (mechanical, same reason)
- Modify: `src/test/java/dev/asante/matheaufgabenmod/budget/BudgetStateTest.java`
- Modify: `src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java` (mechanical compile-fixes + boundary-tick updates only — field/method renames deferred to Task 2)

**Interfaces:**
- Produces: `BudgetPhase.WARNING` (replaces `BudgetPhase.EXPIRED`), `BudgetState.WARNING_TICKS` (replaces `BudgetState.GRACE_TICKS`, same value: `5 * TICKS_PER_MINUTE` = 6000). `BudgetState.remainingTicks()` now returns `budgetTicks - elapsedTicks` for both `ACTIVE` and `WARNING` (no more phase-dependent grace formula). `BudgetState.tick()`: `ACTIVE → WARNING` at `elapsedTicks >= budgetTicks - WARNING_TICKS` (only when `budgetTicks > WARNING_TICKS`); `ACTIVE → HARD_TIMEOUT` directly at `elapsedTicks >= budgetTicks` when the budget is too short for a warning window; `WARNING → HARD_TIMEOUT` at `elapsedTicks >= budgetTicks`.
- Consumes: nothing new from other tasks.

- [ ] **Step 1: Update `BudgetPhase.java`**

```java
package dev.asante.matheaufgabenmod.budget;

/** The five lifecycle phases of the play-budget timer. See the spec for transitions. */
public enum BudgetPhase {
    WAITING_FOR_WORLD,
    WAITING_FOR_BUDGET,
    ACTIVE,
    WARNING,
    HARD_TIMEOUT
}
```

- [ ] **Step 2: Rewrite `BudgetState.java`**

```java
package dev.asante.matheaufgabenmod.budget;

/**
 * Immutable state record for the play-budget timer. Every transition method
 * returns a new instance — no mutation. Tick semantics: ticks only accumulate
 * while in {@link BudgetPhase#ACTIVE} or {@link BudgetPhase#WARNING}; waiting
 * phases and hard timeout are static.
 *
 * <p>The caller (BudgetTracker) is responsible for not invoking
 * {@link #tick()} when the game is paused.
 */
public record BudgetState(BudgetPhase phase, int budgetTicks, int elapsedTicks) {

    public static final int TICKS_PER_MINUTE = 60 * 20;     // 1200
    public static final int WARNING_TICKS = 5 * TICKS_PER_MINUTE; // 6000 (5 minutes)
    public static final int MIN_BUDGET_MINUTES = 1;
    public static final int MAX_BUDGET_MINUTES = 1440;       // 24 hours

    public static BudgetState initial() {
        return new BudgetState(BudgetPhase.WAITING_FOR_WORLD, 0, 0);
    }

    public BudgetState worldLoaded() {
        if (phase == BudgetPhase.WAITING_FOR_WORLD) {
            return new BudgetState(BudgetPhase.WAITING_FOR_BUDGET, 0, 0);
        }
        return this;
    }

    public BudgetState worldUnloaded() {
        return BudgetState.initial();
    }

    public BudgetState budgetSubmitted(int minutes) {
        if (phase != BudgetPhase.WAITING_FOR_BUDGET) return this;
        int clamped = Math.max(MIN_BUDGET_MINUTES, Math.min(MAX_BUDGET_MINUTES, minutes));
        return new BudgetState(BudgetPhase.ACTIVE, clamped * TICKS_PER_MINUTE, 0);
    }

    public BudgetState tick() {
        return switch (phase) {
            case ACTIVE -> {
                int next = elapsedTicks + 1;
                if (next >= budgetTicks) {
                    yield new BudgetState(BudgetPhase.HARD_TIMEOUT, budgetTicks, next);
                }
                if (budgetTicks > WARNING_TICKS && next >= budgetTicks - WARNING_TICKS) {
                    yield new BudgetState(BudgetPhase.WARNING, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.ACTIVE, budgetTicks, next);
            }
            case WARNING -> {
                int next = elapsedTicks + 1;
                if (next >= budgetTicks) {
                    yield new BudgetState(BudgetPhase.HARD_TIMEOUT, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.WARNING, budgetTicks, next);
            }
            default -> this;
        };
    }

    /**
     * Ticks remaining until the budget is fully used up (HARD_TIMEOUT).
     * - In {@link BudgetPhase#ACTIVE} or {@link BudgetPhase#WARNING}: budgetTicks - elapsedTicks.
     * - Otherwise: 0.
     */
    public int remainingTicks() {
        return switch (phase) {
            case ACTIVE, WARNING -> Math.max(0, budgetTicks - elapsedTicks);
            default -> 0;
        };
    }
}
```

- [ ] **Step 3: Fix the compile-time reference in `BudgetTracker.java`**

Replace (around line 40):

```java
        if (before.phase() != BudgetPhase.EXPIRED && state.phase() == BudgetPhase.EXPIRED) {
            surface.openSoftExpired();
        }
```

with:

```java
        if (before.phase() != BudgetPhase.WARNING && state.phase() == BudgetPhase.WARNING) {
            surface.openSoftExpired();
        }
```

(The `openSoftExpired()` call keeps its current name for now — renamed to `openWarning()` in Task 2.)

- [ ] **Step 4: Fix the compile-time reference in `BudgetHudRenderer.java`**

Replace (around line 47):

```java
            case EXPIRED -> { label = Component.literal("Schlusszeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFF5555; }
```

with:

```java
            case WARNING -> { label = Component.literal("Schlusszeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFF5555; }
```

- [ ] **Step 5: Rewrite `BudgetStateTest.java`**

```java
package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetStateTest {

    private static final int TPM = BudgetState.TICKS_PER_MINUTE;     // 1200
    private static final int WARNING_TICKS = BudgetState.WARNING_TICKS;    // 6000 (5 min)

    @Test
    void initialIsWaitingForWorldWithNoBudget() {
        BudgetState s = BudgetState.initial();
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, s.phase());
        assertEquals(0, s.budgetTicks());
        assertEquals(0, s.elapsedTicks());
    }

    @Test
    void worldLoadedAdvancesToWaitingForBudget() {
        BudgetState s = BudgetState.initial().worldLoaded();
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s.phase());
    }

    @Test
    void worldLoadedIsIdempotentInWaitingForBudget() {
        BudgetState s = BudgetState.initial().worldLoaded().worldLoaded();
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s.phase());
    }

    @Test
    void worldUnloadedResetsToWaitingForWorldFromAnyPhase() {
        BudgetState s = BudgetState.initial()
                .worldLoaded()
                .budgetSubmitted(30)
                .tick().tick().tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        BudgetState after = s.worldUnloaded();
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, after.phase());
        assertEquals(0, after.elapsedTicks(), "elapsed resets on world unload");
        assertEquals(0, after.budgetTicks(), "budget resets on world unload");
    }

    @Test
    void budgetSubmittedTransitionsToActive() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(30);
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        assertEquals(30 * TPM, s.budgetTicks());
        assertEquals(0, s.elapsedTicks());
    }

    @Test
    void budgetSubmittedIsIgnoredIfNotWaitingForBudget() {
        BudgetState s = BudgetState.initial();  // WAITING_FOR_WORLD
        BudgetState after = s.budgetSubmitted(30);
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, after.phase());
        assertEquals(0, after.budgetTicks());
    }

    @Test
    void tickIncrementsElapsedInActive() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(30).tick().tick();
        assertEquals(2, s.elapsedTicks());
        assertEquals(BudgetPhase.ACTIVE, s.phase());
    }

    @Test
    void tickInWaitingPhasesIsNoOp() {
        BudgetState s = BudgetState.initial().tick();
        assertEquals(0, s.elapsedTicks());
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, s.phase());

        BudgetState s2 = BudgetState.initial().worldLoaded().tick();
        assertEquals(0, s2.elapsedTicks());
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s2.phase());
    }

    @Test
    void tickAtWarningBoundaryTransitionsToWarning() {
        // 10-minute budget: the 5-min warning window opens 5 minutes before the end,
        // i.e. once 5 of the 10 minutes have elapsed.
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = s.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
    }

    @Test
    void tickInWarningAccumulatesUntilHardTimeout() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = s.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary; i++) s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
        // Need WARNING_TICKS more ticks to reach the actual budget end.
        for (int i = 0; i < WARNING_TICKS - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
        s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
    }

    @Test
    void shortBudgetSkipsWarningPhaseEntirely() {
        // A 5-minute budget equals WARNING_TICKS exactly — still too short to carve
        // out a separate warning window, so it must go straight to HARD_TIMEOUT.
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(5);
        for (int i = 0; i < WARNING_TICKS - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase(), "still active one tick before the end");
        s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase(), "5-min budget skips WARNING entirely");
    }

    @Test
    void remainingTicksReflectsPhase() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(2);  // 2400 ticks
        for (int i = 0; i < TPM; i++) s = s.tick();  // halfway
        assertEquals(TPM, s.remainingTicks(), "halfway through 2-min budget = 1 min remaining");

        BudgetState w = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = w.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary; i++) w = w.tick();
        assertEquals(BudgetPhase.WARNING, w.phase());
        assertEquals(WARNING_TICKS, w.remainingTicks(), "just-entered WARNING: full warning window remaining");
    }

    @Test
    void hardTimeoutTicksAreNoOps() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(1);
        for (int i = 0; i < TPM; i++) s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
        int elapsedAtTimeout = s.elapsedTicks();
        BudgetState after = s.tick().tick().tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, after.phase());
        assertEquals(elapsedAtTimeout, after.elapsedTicks(), "no further ticking after hard timeout");
    }
}
```

- [ ] **Step 6: Fix compile + boundary updates in `BudgetTrackerTest.java`**

Replace the constant declaration (line 14):

```java
    private static final int GRACE = BudgetState.GRACE_TICKS;
```

with:

```java
    private static final int WARNING_TICKS = BudgetState.WARNING_TICKS;
```

Replace the two boundary tests (`crossingBudgetFiresSoftExpiredExactlyOnce` and `crossingGraceFiresHardTimeoutExactlyOnce`):

```java
    @Test
    void crossingWarningBoundaryFiresSoftExpiredExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(10);  // 10-minute budget, long enough for a warning window
        tickN(t, s, 10 * TPM - WARNING_TICKS);
        assertEquals(BudgetPhase.WARNING, t.state().phase());
        assertEquals(1, s.softExpiredCount, "soft-expired called once on transition");
        tickN(t, s, 100);
        assertEquals(1, s.softExpiredCount, "and not again while in WARNING");
    }

    @Test
    void crossingBudgetFiresHardTimeoutExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(10);
        tickN(t, s, 10 * TPM);
        assertEquals(BudgetPhase.HARD_TIMEOUT, t.state().phase());
        assertEquals(1, s.hardTimeoutCount);
        tickN(t, s, 100);
        assertEquals(1, s.hardTimeoutCount, "no repeat firing in HARD_TIMEOUT");
    }
```

- [ ] **Step 7: Run the full test suite**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (including the rewritten `BudgetStateTest` and `BudgetTrackerTest`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetPhase.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetState.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHudRenderer.java \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetStateTest.java \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java
git commit -m "Shift budget warning trigger to 5min before expiry, not after

Renames EXPIRED -> WARNING and GRACE_TICKS -> WARNING_TICKS. The
warning phase now fires at budget-5min instead of at budget, and
HARD_TIMEOUT fires at exactly budget instead of budget+5min. Total
playtime now equals exactly the chosen budget. Budgets <= 5 minutes
skip the warning phase entirely."
```

---

## Task 2: Rename the warning callback (`openSoftExpired` → `openWarning`)

**Files:**
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSurface.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java:41`
- Modify: `src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java`

**Interfaces:**
- Consumes: `BudgetPhase.WARNING` from Task 1.
- Produces: `BudgetSurface.openWarning()` (replaces `openSoftExpired()`). `MinecraftBudgetSurface.openWarning()` still constructs `new BudgetSoftExpiredScreen()` for now — that class is renamed in Task 3.

- [ ] **Step 1: Rename the interface method in `BudgetSurface.java`**

Replace:

```java
    /** Open the dismissable "Zeit ist um" popup. */
    void openSoftExpired();
```

with:

```java
    /** Open the dismissable pre-expiry warning popup. */
    void openWarning();
```

- [ ] **Step 2: Rename the implementation in `MinecraftBudgetSurface.java`**

Replace:

```java
    @Override
    public void openSoftExpired() {
        client.setScreen(new BudgetSoftExpiredScreen());
    }
```

with:

```java
    @Override
    public void openWarning() {
        client.setScreen(new BudgetSoftExpiredScreen());
    }
```

- [ ] **Step 3: Rename the call site in `BudgetTracker.java`**

Replace (from Task 1's edit):

```java
        if (before.phase() != BudgetPhase.WARNING && state.phase() == BudgetPhase.WARNING) {
            surface.openSoftExpired();
        }
```

with:

```java
        if (before.phase() != BudgetPhase.WARNING && state.phase() == BudgetPhase.WARNING) {
            surface.openWarning();
        }
```

- [ ] **Step 4: Rename `FakeSurface`'s field/override and update assertions in `BudgetTrackerTest.java`**

Replace:

```java
        int softExpiredCount = 0;
```

with:

```java
        int warningCount = 0;
```

Replace:

```java
        @Override public void openSoftExpired() { softExpiredCount++; }
```

with:

```java
        @Override public void openWarning() { warningCount++; }
```

In `crossingWarningBoundaryFiresSoftExpiredExactlyOnce` (added in Task 1), replace both `s.softExpiredCount` occurrences with `s.warningCount`, and rename the test itself to `crossingWarningBoundaryFiresWarningExactlyOnce`:

```java
    @Test
    void crossingWarningBoundaryFiresWarningExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(10);  // 10-minute budget, long enough for a warning window
        tickN(t, s, 10 * TPM - WARNING_TICKS);
        assertEquals(BudgetPhase.WARNING, t.state().phase());
        assertEquals(1, s.warningCount, "warning called once on transition");
        tickN(t, s, 100);
        assertEquals(1, s.warningCount, "and not again while in WARNING");
    }
```

- [ ] **Step 5: Add a tracker-level test for the short-budget skip case**

Add to `BudgetTrackerTest.java`, after `crossingBudgetFiresHardTimeoutExactlyOnce`:

```java
    @Test
    void shortBudgetSkipsWarningAndGoesStraightToHardTimeout() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(1);  // 1-minute budget, shorter than the 5-min warning window
        tickN(t, s, TPM);
        assertEquals(BudgetPhase.HARD_TIMEOUT, t.state().phase());
        assertEquals(0, s.warningCount, "warning window skipped for budgets <= WARNING_TICKS");
        assertEquals(1, s.hardTimeoutCount);
    }
```

- [ ] **Step 6: Run the full test suite**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSurface.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java
git commit -m "Rename BudgetSurface.openSoftExpired to openWarning

Keeps the interface's naming honest now that this callback fires
before expiry, not after."
```

---

## Task 3: Rename the warning screen and fix its title copy

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetWarningScreen.java` (via `git mv` from `BudgetSoftExpiredScreen.java`)
- Delete: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java` (via the `git mv`)
- Modify: `src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java`
- Modify: `src/main/resources/assets/matheaufgabenmod/lang/de_de.json`

**Interfaces:**
- Consumes: `BudgetSurface.openWarning()` from Task 2.
- Produces: `BudgetWarningScreen` (public no-arg constructor, same behavior as the old `BudgetSoftExpiredScreen`).

- [ ] **Step 1: Rename the file, preserving history**

```bash
git mv src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java \
       src/main/java/dev/asante/matheaufgabenmod/budget/BudgetWarningScreen.java
```

- [ ] **Step 2: Rename the class inside the moved file**

Replace:

```java
public final class BudgetSoftExpiredScreen extends Screen {

    public BudgetSoftExpiredScreen() {
```

with:

```java
public final class BudgetWarningScreen extends Screen {

    public BudgetWarningScreen() {
```

(No other content in the file changes — same `isPauseScreen`, `shouldCloseOnEsc`, `init`, `keyPressed`, `onClose`, `extractRenderState`.)

- [ ] **Step 3: Update the reference in `MinecraftBudgetSurface.java`**

Replace:

```java
    @Override
    public void openWarning() {
        client.setScreen(new BudgetSoftExpiredScreen());
    }
```

with:

```java
    @Override
    public void openWarning() {
        client.setScreen(new BudgetWarningScreen());
    }
```

- [ ] **Step 4: Fix the misleading title copy in `de_de.json`**

Replace:

```json
    "matheaufgabenmod.budget.soft.title": "Zeit ist um!",
```

with:

```json
    "matheaufgabenmod.budget.soft.title": "Gleich ist Schluss!",
```

(The key name stays `budget.soft.title` — only the value changes. The subtitle,
`"Du hast noch 5 Minuten zum Aufräumen."`, already reads correctly as a pre-warning and is
unchanged.)

- [ ] **Step 5: Run the full test suite**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (no test references the screen class directly, so this is a compile-and-green check).

- [ ] **Step 6: Manually verify in-game**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew runClient`

In the dev client: enter a world, choose "Eigene Zeit…" and submit `6` minutes. After 1 minute of
active play, the warning screen should appear with title "Gleich ist Schluss!" and subtitle "Du
hast noch 5 Minuten zum Aufräumen.", and the HUD should read `Schlusszeit: 5:00` counting down.
Dismiss it and confirm the hard-timeout screen appears at the 6-minute mark (5 minutes later),
not at 6+5=11 minutes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetWarningScreen.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java \
        src/main/resources/assets/matheaufgabenmod/lang/de_de.json
git commit -m "Rename BudgetSoftExpiredScreen to BudgetWarningScreen

Also fixes the title copy (\"Zeit ist um!\" -> \"Gleich ist Schluss!\"),
since the screen now opens 5 minutes before expiry, not after."
```

---

## Task 4: Update docs (`CLAUDE.md`, `README.md`)

**Files:**
- Modify: `CLAUDE.md:64-75`
- Modify: `README.md:63-80`

**Interfaces:**
- Consumes: the renamed symbols from Tasks 1–3 (`WARNING`, `BudgetWarningScreen`, `openWarning`).
- Produces: nothing (docs only).

- [ ] **Step 1: Update the `budget/` package description in `CLAUDE.md`**

Replace (lines 64-75):

```markdown
- **`budget/`** — `BudgetTracker` runs a 5-state machine (WAITING_FOR_WORLD → WAITING_FOR_BUDGET
  → ACTIVE → EXPIRED → HARD_TIMEOUT) ticked from `ClientTickEvents.END_CLIENT_TICK`. The
  `BudgetSurface` interface is the test seam (same pattern as `ClientSurface` in `timer/`).
  Three Screen subclasses (`BudgetQueryScreen`, `BudgetSoftExpiredScreen`,
  `BudgetHardTimeoutScreen`) handle entry, soft expiry, and hard expiry. `BudgetQueryScreen`
  starts in preset mode with three buttons (30 min / 60 min / "Eigene Zeit…"); the custom
  option calls `rebuildWidgets()` to swap the panel to a text-input flow. `BudgetHardTimeoutScreen`
  has only a "Spiel beenden" button calling `Minecraft.getInstance().stop()` for graceful
  save & quit. `BudgetHudRenderer` implements `HudElement` and registers via
  `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ...)` for the
  top-right Restzeit/Schlusszeit overlay — the 26.x extract-then-render pattern replaces
  the 1.21.x `HudRenderCallback`. State is session-local — leaving the world resets it.
```

with:

```markdown
- **`budget/`** — `BudgetTracker` runs a 5-state machine (WAITING_FOR_WORLD → WAITING_FOR_BUDGET
  → ACTIVE → WARNING → HARD_TIMEOUT) ticked from `ClientTickEvents.END_CLIENT_TICK`. `WARNING`
  fires 5 minutes *before* the chosen budget runs out (not after) and `HARD_TIMEOUT` fires at
  exactly the chosen budget — total playtime equals exactly what the kid picked. Budgets ≤ 5
  minutes skip `WARNING` entirely. The `BudgetSurface` interface is the test seam (same pattern
  as `ClientSurface` in `timer/`). Three Screen subclasses (`BudgetQueryScreen`,
  `BudgetWarningScreen`, `BudgetHardTimeoutScreen`) handle entry, pre-expiry warning, and hard
  expiry. `BudgetQueryScreen` starts in preset mode with three buttons (30 min / 60 min /
  "Eigene Zeit…"); the custom option calls `rebuildWidgets()` to swap the panel to a
  text-input flow. `BudgetHardTimeoutScreen` has only a "Spiel beenden" button calling
  `Minecraft.getInstance().stop()` for graceful save & quit. `BudgetHudRenderer` implements
  `HudElement` and registers via `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ...)`
  for the top-right Restzeit/Schlusszeit overlay — the 26.x extract-then-render pattern replaces
  the 1.21.x `HudRenderCallback`. State is session-local — leaving the world resets it.
```

- [ ] **Step 2: Update the "Play-budget timer" section in `README.md`**

Replace (lines 72-76):

```markdown
A HUD in the top-right shows the remaining time as `Restzeit: MM:SS` in white. When the
budget runs out, a "Zeit ist um!" popup appears that can be dismissed; the kid then has a
5-minute grace period (HUD turns red and counts down `Schlusszeit: M:SS`) to reach a
save point. After grace, a forced-quit popup appears with only a "Spiel beenden" button
— clicking it triggers Minecraft's normal save-and-quit. No data is lost.
```

with:

```markdown
A HUD in the top-right shows the remaining time as `Restzeit: MM:SS` in white. Five minutes
before the budget runs out, a "Gleich ist Schluss!" popup appears that can be dismissed as a
heads-up to wrap up (HUD turns red and counts down `Schlusszeit: M:SS`); budgets of 5 minutes
or less skip this warning. When the chosen budget is fully used up, a forced-quit popup appears
with only a "Spiel beenden" button — clicking it triggers Minecraft's normal save-and-quit. No
data is lost. Total playtime always equals the chosen budget exactly.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "Update docs for budget-warning-before-expiry timing change"
```
