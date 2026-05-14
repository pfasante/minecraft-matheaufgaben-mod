# Matheaufgaben Play-Budget Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Spec/plan/CLAUDE.md/README precedent is the MVP.

**Goal:** Add a per-session play-time budget. On world entry, ask the kid how many minutes they want to play. Show remaining time top-right. After expiry, a 5-minute grace period; after that, force a graceful save & quit. The system pauses with Minecraft's pause state, so it cooperates with the math-prompt scheduler automatically.

**Architecture:** New `budget/` package. `BudgetState` (record) holds counters and the state-machine state. `BudgetTracker` runs on every client tick, transitions states, opens screens via a `BudgetSurface` test seam. Three new Screen subclasses for query/soft-expired/hard-timeout. A HudRenderCallback for the top-right HUD overlay. `MinecraftBudgetSurface` is the production implementation; tests use a `FakeBudgetSurface`.

**Spec:** `docs/superpowers/specs/2026-05-11-matheaufgaben-play-budget-design.md` — read this FIRST for design rationale and the state-machine diagram.

**Tech stack:** Unchanged from MVP. Java 21, Fabric Loom 1.8, MC 1.21.4, JUnit 5.

**Important conventions (carry over from MVP):**
- NO `// src/main/java/...` path-comment headers at the top of files; line 1 is `package`.
- All gradle invocations must use `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` — system default is JDK 25 which will not work with Loom 1.8.
- The budget feature relies on the existing pause semantics: `client.isPaused()` is true whenever any `Screen` is open in singleplayer. The `BudgetSurface.isPaused()` method just forwards this.

---

## Task 1: BudgetState + state machine (TDD, no Minecraft deps)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetPhase.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetState.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/budget/BudgetStateTest.java`

`BudgetPhase` is the 5-value enum (`WAITING_FOR_WORLD`, `WAITING_FOR_BUDGET`, `ACTIVE`, `EXPIRED`, `HARD_TIMEOUT`). `BudgetState` is an immutable record carrying the phase + `budgetTicks` + `elapsedTicks`, with pure transition methods.

### Step 1: Write the failing tests

`src/test/java/dev/asante/matheaufgabenmod/budget/BudgetStateTest.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetStateTest {

    private static final int TPM = BudgetState.TICKS_PER_MINUTE; // 1200
    private static final int GRACE = BudgetState.GRACE_TICKS;    // 6000 (5 min)

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
    void tickAtBudgetBoundaryTransitionsToExpired() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(1);  // 1200 ticks
        for (int i = 0; i < TPM - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        s = s.tick();  // 1200th tick
        assertEquals(BudgetPhase.EXPIRED, s.phase());
    }

    @Test
    void tickInExpiredAccumulatesGraceUntilHardTimeout() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(1);
        for (int i = 0; i < TPM; i++) s = s.tick();
        assertEquals(BudgetPhase.EXPIRED, s.phase());
        // Need GRACE more ticks to hit HARD_TIMEOUT.
        for (int i = 0; i < GRACE - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.EXPIRED, s.phase());
        s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
    }

    @Test
    void remainingTicksReflectsPhase() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(2);  // 2400 ticks
        for (int i = 0; i < TPM; i++) s = s.tick();  // halfway
        assertEquals(TPM, s.remainingTicks(), "halfway through 2-min budget = 1 min remaining");
        for (int i = 0; i < TPM; i++) s = s.tick();
        assertEquals(BudgetPhase.EXPIRED, s.phase());
        assertEquals(GRACE, s.remainingTicks(), "just-expired: full grace remaining");
    }

    @Test
    void hardTimeoutTicksAreNoOps() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(1);
        for (int i = 0; i < TPM + GRACE; i++) s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
        int elapsedAtTimeout = s.elapsedTicks();
        BudgetState after = s.tick().tick().tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, after.phase());
        assertEquals(elapsedAtTimeout, after.elapsedTicks(), "no further ticking after hard timeout");
    }
}
```

### Step 2: Run, confirm fail (compile error)

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests BudgetStateTest
```

### Step 3: Implement `BudgetPhase.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetPhase.java`:
```java
package dev.asante.matheaufgabenmod.budget;

/** The five lifecycle phases of the play-budget timer. See the spec for transitions. */
public enum BudgetPhase {
    WAITING_FOR_WORLD,
    WAITING_FOR_BUDGET,
    ACTIVE,
    EXPIRED,
    HARD_TIMEOUT
}
```

### Step 4: Implement `BudgetState.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetState.java`:
```java
package dev.asante.matheaufgabenmod.budget;

/**
 * Immutable state record for the play-budget timer. Every transition method
 * returns a new instance — no mutation. Tick semantics: ticks only accumulate
 * while in {@link BudgetPhase#ACTIVE} or {@link BudgetPhase#EXPIRED}; waiting
 * phases and hard timeout are static.
 *
 * <p>The caller (BudgetTracker, Task 2) is responsible for not invoking
 * {@link #tick()} when the game is paused.
 */
public record BudgetState(BudgetPhase phase, int budgetTicks, int elapsedTicks) {

    public static final int TICKS_PER_MINUTE = 60 * 20;     // 1200
    public static final int GRACE_TICKS = 5 * TICKS_PER_MINUTE; // 6000 (5 minutes)
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
                    yield new BudgetState(BudgetPhase.EXPIRED, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.ACTIVE, budgetTicks, next);
            }
            case EXPIRED -> {
                int next = elapsedTicks + 1;
                if (next >= budgetTicks + GRACE_TICKS) {
                    yield new BudgetState(BudgetPhase.HARD_TIMEOUT, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.EXPIRED, budgetTicks, next);
            }
            default -> this;
        };
    }

    /**
     * Ticks remaining until the next phase transition.
     * - In {@link BudgetPhase#ACTIVE}: ticks until expiry.
     * - In {@link BudgetPhase#EXPIRED}: ticks until hard timeout.
     * - Otherwise: 0.
     */
    public int remainingTicks() {
        return switch (phase) {
            case ACTIVE -> Math.max(0, budgetTicks - elapsedTicks);
            case EXPIRED -> Math.max(0, budgetTicks + GRACE_TICKS - elapsedTicks);
            default -> 0;
        };
    }
}
```

### Step 5: Run, confirm pass

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests BudgetStateTest
```

Expected: 11 tests passed.

### Step 6: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/budget/ \
        src/test/java/dev/asante/matheaufgabenmod/budget/
git commit -m "Add BudgetState and BudgetPhase with state-machine transitions"
```

---

## Task 2: BudgetSurface + BudgetTracker (TDD with FakeSurface)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSurface.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java`

`BudgetSurface` is the same test-seam pattern as `ClientSurface` in `timer/`. `BudgetTracker` owns the `BudgetState` and orchestrates calls into the surface at the right moments.

### Step 1: Write `BudgetSurface.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSurface.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import java.util.function.IntConsumer;

/**
 * Narrow façade over MinecraftClient for the play-budget timer. Mirrors the
 * pattern used by ClientSurface in the timer package: lets BudgetTracker be
 * fully unit-testable in plain JUnit, without booting Minecraft.
 */
public interface BudgetSurface {
    boolean hasWorld();
    boolean isPaused();

    /** Open the modal "how many minutes?" screen; invoke the consumer with the user's choice. */
    void openBudgetQuery(IntConsumer onSubmit);

    /** Open the dismissable "Zeit ist um" popup. */
    void openSoftExpired();

    /** Open the undismissable "Spiel beenden" popup. */
    void openHardTimeout();

    /** Update the HUD's idea of the remaining state. Called every tick. */
    void updateHud(BudgetState state);
}
```

### Step 2: Write the failing tests

`src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

class BudgetTrackerTest {

    private static final int TPM = BudgetState.TICKS_PER_MINUTE;
    private static final int GRACE = BudgetState.GRACE_TICKS;

    /** Test double recording surface interactions and exposing controllable inputs. */
    private static final class FakeSurface implements BudgetSurface {
        boolean hasWorld = false;
        boolean isPaused = false;
        IntConsumer pendingBudgetCallback;
        int softExpiredCount = 0;
        int hardTimeoutCount = 0;
        final List<BudgetState> hudHistory = new ArrayList<>();

        @Override public boolean hasWorld() { return hasWorld; }
        @Override public boolean isPaused() { return isPaused; }
        @Override public void openBudgetQuery(IntConsumer onSubmit) { this.pendingBudgetCallback = onSubmit; }
        @Override public void openSoftExpired() { softExpiredCount++; }
        @Override public void openHardTimeout() { hardTimeoutCount++; }
        @Override public void updateHud(BudgetState s) { hudHistory.add(s); }
    }

    private static void tickN(BudgetTracker tracker, FakeSurface surface, int n) {
        for (int i = 0; i < n; i++) tracker.onTick(surface);
    }

    @Test
    void noWorldNoBudgetQuery() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        tickN(t, s, 100);
        assertNull(s.pendingBudgetCallback, "no world → no query opened");
    }

    @Test
    void worldLoadOpensBudgetQuery() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        assertNotNull(s.pendingBudgetCallback, "query opens on first tick after world available");
    }

    @Test
    void doesNotReopenBudgetQueryOnSubsequentTicks() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        IntConsumer first = s.pendingBudgetCallback;
        s.pendingBudgetCallback = null;
        for (int i = 0; i < 10; i++) t.onTick(s);
        assertNull(s.pendingBudgetCallback, "tracker must not reopen the query on every tick");
        assertNotNull(first);
    }

    @Test
    void budgetSubmissionTransitionsToActive() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(30);
        assertEquals(BudgetPhase.ACTIVE, t.state().phase());
        assertEquals(30 * TPM, t.state().budgetTicks());
    }

    @Test
    void pausedTicksDoNotAdvanceElapsed() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(10);
        s.isPaused = true;
        tickN(t, s, 100);
        assertEquals(0, t.state().elapsedTicks(), "paused = no advance");
    }

    @Test
    void crossingBudgetFiresSoftExpiredExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(1);  // 1200 tick budget
        tickN(t, s, TPM);
        assertEquals(BudgetPhase.EXPIRED, t.state().phase());
        assertEquals(1, s.softExpiredCount, "soft-expired called once on transition");
        tickN(t, s, 100);
        assertEquals(1, s.softExpiredCount, "and not again while in EXPIRED");
    }

    @Test
    void crossingGraceFiresHardTimeoutExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(1);
        tickN(t, s, TPM + GRACE);
        assertEquals(BudgetPhase.HARD_TIMEOUT, t.state().phase());
        assertEquals(1, s.hardTimeoutCount);
        tickN(t, s, 100);
        assertEquals(1, s.hardTimeoutCount, "no repeat firing in HARD_TIMEOUT");
    }

    @Test
    void leavingWorldResetsToInitial() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(60);
        tickN(t, s, 600);
        assertEquals(600, t.state().elapsedTicks());
        s.hasWorld = false;
        t.onTick(s);
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, t.state().phase());
        // Re-entering opens a fresh query.
        s.pendingBudgetCallback = null;
        s.hasWorld = true;
        t.onTick(s);
        assertNotNull(s.pendingBudgetCallback);
    }

    @Test
    void hudUpdatesEveryTickWithCurrentState() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(2);
        tickN(t, s, 5);
        assertEquals(6, s.hudHistory.size(),
                "1 hud update at world-load + 5 ticks under ACTIVE");
        assertEquals(BudgetPhase.ACTIVE, s.hudHistory.get(s.hudHistory.size() - 1).phase());
    }
}
```

### Step 3: Run, confirm fail (compile error)

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests BudgetTrackerTest
```

### Step 4: Implement `BudgetTracker.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java`:
```java
package dev.asante.matheaufgabenmod.budget;

/**
 * Drives the {@link BudgetState} state machine and calls into a {@link BudgetSurface}
 * at the right transitions. Designed to be invoked from a per-tick callback
 * (e.g. Fabric's ClientTickEvents.END_CLIENT_TICK).
 *
 * <p>The tracker is single-threaded — Minecraft's client thread is the only caller.
 */
public final class BudgetTracker {

    private BudgetState state = BudgetState.initial();

    public BudgetState state() { return state; }

    public void onTick(BudgetSurface surface) {
        BudgetState before = state;

        // 1. Reconcile world presence first.
        if (!surface.hasWorld()) {
            if (state.phase() != BudgetPhase.WAITING_FOR_WORLD) {
                state = state.worldUnloaded();
            }
            surface.updateHud(state);
            return;
        }
        if (state.phase() == BudgetPhase.WAITING_FOR_WORLD) {
            state = state.worldLoaded();
            surface.openBudgetQuery(this::onBudgetSubmitted);
            surface.updateHud(state);
            return;
        }

        // 2. Tick only while not paused.
        if (!surface.isPaused()) {
            state = state.tick();
        }

        // 3. Fire one-shot callbacks on phase transitions.
        if (before.phase() != BudgetPhase.EXPIRED && state.phase() == BudgetPhase.EXPIRED) {
            surface.openSoftExpired();
        }
        if (before.phase() != BudgetPhase.HARD_TIMEOUT && state.phase() == BudgetPhase.HARD_TIMEOUT) {
            surface.openHardTimeout();
        }

        // 4. Update HUD every tick.
        surface.updateHud(state);
    }

    /**
     * Invoked by the budget-query screen when the user submits a value.
     * Clamping and phase guarding are inside BudgetState.budgetSubmitted.
     */
    public void onBudgetSubmitted(int minutes) {
        state = state.budgetSubmitted(minutes);
    }
}
```

### Step 5: Run, confirm pass

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests BudgetTrackerTest
```

Expected: 9 tests passed.

### Step 6: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSurface.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetTracker.java \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetTrackerTest.java
git commit -m "Add BudgetSurface and BudgetTracker; full state-machine driving"
```

---

## Task 3: Budget screens (BudgetQueryScreen, SoftExpiredScreen, HardTimeoutScreen)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetQueryScreen.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHardTimeoutScreen.java`

Three Minecraft `Screen` subclasses. UI code only — no unit tests (verified manually via `runClient`, matching `PromptScreen`'s pattern from the MVP).

### Step 1: Implement `BudgetQueryScreen.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetQueryScreen.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

public final class BudgetQueryScreen extends Screen {

    private static final int DEFAULT_MINUTES = 60;

    private final IntConsumer onSubmit;
    private TextFieldWidget inputField;
    private Text feedback = Text.empty();

    public BudgetQueryScreen(IntConsumer onSubmit) {
        super(Text.translatable("matheaufgabenmod.budget.query.title"));
        this.onSubmit = onSubmit;
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.inputField = new TextFieldWidget(
                this.textRenderer,
                cx - 50, cy + 10, 100, 20,
                Text.translatable("matheaufgabenmod.budget.query.title")
        );
        this.inputField.setMaxLength(4);
        this.inputField.setText(Integer.toString(DEFAULT_MINUTES));
        this.addDrawableChild(this.inputField);
        this.setInitialFocus(this.inputField);

        ButtonWidget submit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.query.submit"),
                btn -> onSubmitClicked()
        ).dimensions(cx - 50, cy + 40, 100, 20).build();
        this.addDrawableChild(submit);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (keyCode == 257 || keyCode == 335) {
            onSubmitClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmitClicked() {
        Integer parsed = parseMinutes(inputField.getText());
        if (parsed == null) {
            inputField.setText("");
            feedback = Text.translatable("matheaufgabenmod.budget.query.invalid");
            return;
        }
        onSubmit.accept(parsed);
        MinecraftClient.getInstance().setScreen(null);
    }

    static Integer parseMinutes(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < BudgetState.MIN_BUDGET_MINUTES || v > BudgetState.MAX_BUDGET_MINUTES) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 60;
        int unitY = this.height / 2 + 35;
        int feedbackY = this.height / 2 + 75;

        // Title (1.5x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.query.unit"),
                cx, unitY, 0xFFAAAAAA);

        if (!feedback.getString().isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
```

### Step 2: Implement `BudgetSoftExpiredScreen.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BudgetSoftExpiredScreen extends Screen {

    public BudgetSoftExpiredScreen() {
        super(Text.translatable("matheaufgabenmod.budget.soft.title"));
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        ButtonWidget ok = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.soft.ok"),
                btn -> close()
        ).dimensions(cx - 50, cy + 30, 100, 20).build();
        this.addDrawableChild(ok);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 50;
        int subtitleY = this.height / 2 - 10;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFCC00);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.soft.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
```

### Step 3: Implement `BudgetHardTimeoutScreen.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHardTimeoutScreen.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BudgetHardTimeoutScreen extends Screen {

    public BudgetHardTimeoutScreen() {
        super(Text.translatable("matheaufgabenmod.budget.hard.title"));
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        ButtonWidget quit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.hard.quit"),
                btn -> MinecraftClient.getInstance().scheduleStop()
        ).dimensions(cx - 60, cy + 40, 120, 20).build();
        this.addDrawableChild(quit);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 50;
        int subtitleY = this.height / 2 - 10;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFF5555);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.hard.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
```

### Step 4: Add German strings to `de_de.json`

Read the current `de_de.json` (it has the MVP's 3 prompt keys + Task 3 of the achievements branch may also add keys — for THIS branch, only the prompt keys exist on `main`). Insert the budget-feature keys, preserving existing entries:

Append into the JSON object (between the existing prompt keys and the closing brace):

```json
    "matheaufgabenmod.budget.query.title": "Wie lange willst du spielen?",
    "matheaufgabenmod.budget.query.submit": "Los geht's!",
    "matheaufgabenmod.budget.query.unit": "Minuten",
    "matheaufgabenmod.budget.query.invalid": "Bitte eine Zahl zwischen 1 und 1440 eingeben",

    "matheaufgabenmod.budget.soft.title": "Zeit ist um!",
    "matheaufgabenmod.budget.soft.subtitle": "Du hast noch 5 Minuten zum Aufräumen.",
    "matheaufgabenmod.budget.soft.ok": "OK",

    "matheaufgabenmod.budget.hard.title": "Wirklich Schluss für heute!",
    "matheaufgabenmod.budget.hard.subtitle": "Drücke „Spiel beenden", dann wird gespeichert und Minecraft beendet.",
    "matheaufgabenmod.budget.hard.quit": "Spiel beenden"
```

Remember to keep the JSON valid: the previous key needs a trailing comma, the last key in the file does not.

### Step 5: Add a tiny unit test for `parseMinutes` static helper

`src/test/java/dev/asante/matheaufgabenmod/budget/BudgetQueryScreenTest.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetQueryScreenTest {

    @Test
    void parsesValidIntegers() {
        assertEquals(1, BudgetQueryScreen.parseMinutes("1"));
        assertEquals(60, BudgetQueryScreen.parseMinutes("60"));
        assertEquals(1440, BudgetQueryScreen.parseMinutes("1440"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(30, BudgetQueryScreen.parseMinutes(" 30 "));
    }

    @Test
    void rejectsOutOfRange() {
        assertNull(BudgetQueryScreen.parseMinutes("0"));
        assertNull(BudgetQueryScreen.parseMinutes("-5"));
        assertNull(BudgetQueryScreen.parseMinutes("1441"));
    }

    @Test
    void rejectsNonInteger() {
        assertNull(BudgetQueryScreen.parseMinutes(""));
        assertNull(BudgetQueryScreen.parseMinutes("abc"));
        assertNull(BudgetQueryScreen.parseMinutes("30.5"));
    }
}
```

### Step 6: Run full test suite

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: 89 + 11 (Task 1) + 9 (Task 2) + 4 (Task 3 helper) = 113 tests.

### Step 7: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetQueryScreen.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetSoftExpiredScreen.java \
        src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHardTimeoutScreen.java \
        src/main/resources/assets/matheaufgabenmod/lang/de_de.json \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetQueryScreenTest.java
git commit -m "Add budget query/soft-expired/hard-timeout screens with German strings"
```

---

## Task 4: HUD renderer

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHudRenderer.java`

The HUD is registered on `HudRenderCallback.EVENT`. It reads the latest `BudgetState` from a holder and draws "Restzeit: MM:SS" (or "Schlusszeit: M:SS" in EXPIRED phase) top-right.

### Step 1: Implement `BudgetHudRenderer.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHudRenderer.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Draws the play-budget HUD overlay top-right. The current state is supplied
 * by {@link BudgetTracker} via {@link #stateHolder()} — a thread-safe atomic
 * reference (client thread writes, render thread reads on Fabric, but render
 * actually runs on the client thread too in 1.21.x; the atomic is defensive).
 */
public final class BudgetHudRenderer implements HudRenderCallback {

    private static final int RIGHT_MARGIN = 10;
    private static final int TOP_MARGIN = 10;

    private final AtomicReference<BudgetState> stateHolder = new AtomicReference<>(BudgetState.initial());

    /** Hand this to BudgetTracker via a setter callback. Tracker writes; renderer reads. */
    public AtomicReference<BudgetState> stateHolder() { return stateHolder; }

    public void register() {
        HudRenderCallback.EVENT.register(this);
    }

    @Override
    public void onHudRender(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        BudgetState s = stateHolder.get();
        Text label;
        int colour;
        switch (s.phase()) {
            case ACTIVE -> { label = Text.literal("Restzeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFFFFFF; }
            case EXPIRED -> { label = Text.literal("Schlusszeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFF5555; }
            default -> { return; }  // no HUD in WAITING_* or HARD_TIMEOUT
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(label);
        int x = ctx.getScaledWindowWidth() - textWidth - RIGHT_MARGIN;
        ctx.drawTextWithShadow(client.textRenderer, label, x, TOP_MARGIN, colour);
    }

    static String formatMmSs(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format("%d:%02d", mm, ss);
    }
}
```

### Step 2: Add a tiny unit test for `formatMmSs`

`src/test/java/dev/asante/matheaufgabenmod/budget/BudgetHudRendererTest.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetHudRendererTest {

    @Test
    void formatsMinutesAndSecondsWithLeadingZero() {
        assertEquals("0:00", BudgetHudRenderer.formatMmSs(0));
        assertEquals("0:01", BudgetHudRenderer.formatMmSs(20));      // 1 second
        assertEquals("1:00", BudgetHudRenderer.formatMmSs(60 * 20)); // 1 minute
        assertEquals("23:45", BudgetHudRenderer.formatMmSs((23 * 60 + 45) * 20));
        assertEquals("0:00", BudgetHudRenderer.formatMmSs(-100));    // negative clamps to 0:00
    }
}
```

### Step 3: Run full test suite

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: 113 + 5 = 118 tests.

### Step 4: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/budget/BudgetHudRenderer.java \
        src/test/java/dev/asante/matheaufgabenmod/budget/BudgetHudRendererTest.java
git commit -m "Add BudgetHudRenderer (top-right Restzeit/Schlusszeit overlay)"
```

---

## Task 5: Wire everything into MatheaufgabenMod

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java`

`MinecraftBudgetSurface` is the production `BudgetSurface` — it opens the real Screens and writes to the HUD-renderer's state holder. `MatheaufgabenMod.onInitializeClient` instantiates the tracker, the surface, and the HUD renderer; registers a second tick listener.

### Step 1: Implement `MinecraftBudgetSurface.java`

`src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java`:
```java
package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/** Production BudgetSurface that opens real Screens and writes to the HUD state holder. */
public final class MinecraftBudgetSurface implements BudgetSurface {

    private final MinecraftClient client;
    private final AtomicReference<BudgetState> hudState;

    public MinecraftBudgetSurface(MinecraftClient client, AtomicReference<BudgetState> hudState) {
        this.client = client;
        this.hudState = hudState;
    }

    @Override
    public boolean hasWorld() { return client.world != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public void openBudgetQuery(IntConsumer onSubmit) {
        client.setScreen(new BudgetQueryScreen(onSubmit));
    }

    @Override
    public void openSoftExpired() {
        client.setScreen(new BudgetSoftExpiredScreen());
    }

    @Override
    public void openHardTimeout() {
        client.setScreen(new BudgetHardTimeoutScreen());
    }

    @Override
    public void updateHud(BudgetState state) {
        hudState.set(state);
    }
}
```

### Step 2: Modify `MatheaufgabenMod.java`

Read the current `MatheaufgabenMod.java`. Replace its body to add the budget wiring. The exact target depends on which branch lands first — both `feat/achievements` and `feat/play-budget-timer` modify the same file. **If you're executing this plan after `feat/achievements` is merged**, your `MatheaufgabenMod.java` already has `HistoryLogger`, `AchievementTracker`, and a composed `Consumer<HistoryEntry>`; add the budget wiring alongside without removing those.

Below is the canonical full-wiring shape assuming **this is the only post-MVP feature merged so far** (i.e., the prerequisite is just `main` post-MVP-and-history-log). Adjust the imports / construction if the achievements branch has already landed.

```java
package dev.asante.matheaufgabenmod;

import dev.asante.matheaufgabenmod.budget.BudgetHudRenderer;
import dev.asante.matheaufgabenmod.budget.BudgetTracker;
import dev.asante.matheaufgabenmod.budget.MinecraftBudgetSurface;
import dev.asante.matheaufgabenmod.config.ConfigLoader;
import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.history.HistoryLogger;
import dev.asante.matheaufgabenmod.timer.ClientSurface;
import dev.asante.matheaufgabenmod.timer.MinecraftClientSurface;
import dev.asante.matheaufgabenmod.timer.PromptScheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Random;

public final class MatheaufgabenMod implements ClientModInitializer {

    public static final String MOD_ID = "matheaufgabenmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(MOD_ID + ".json");
        Path historyPath = configDir.resolve(MOD_ID + "-history.log");

        ModConfig config = ConfigLoader.loadOrCreate(configPath);
        HistoryLogger historyLogger = new HistoryLogger(historyPath);

        Random rng = new Random();
        PromptScheduler scheduler = new PromptScheduler(config, rng);

        // Budget timer wiring.
        BudgetTracker budgetTracker = new BudgetTracker();
        BudgetHudRenderer budgetHud = new BudgetHudRenderer();
        budgetHud.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Math-prompt scheduler tick (unchanged).
            ClientSurface promptSurface = new MinecraftClientSurface(
                    client, scheduler::pickProblem, historyLogger::logAttempt);
            scheduler.onTick(promptSurface);

            // Budget tick.
            MinecraftBudgetSurface budgetSurface = new MinecraftBudgetSurface(
                    client, budgetHud.stateHolder());
            budgetTracker.onTick(budgetSurface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} section spec(s), history={}, budget-timer enabled",
                MOD_ID, config.intervalMinutes(), config.sectionSpecs().size(), historyPath);
    }
}
```

### Step 3: Run full test suite

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: 118 tests still pass; no new tests in this task.

### Step 4: Skip — manual `runClient` smoke test

Deferred to the human. After this and Task 6 land:
1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew runClient`
2. Enter a world. Expected: `BudgetQueryScreen` opens. Type `2` minutes, press Enter.
3. World resumes. HUD shows "Restzeit: 1:59" top-right, counting down.
4. Open Esc menu. Confirm HUD freezes.
5. Wait until "Restzeit: 0:00" → `BudgetSoftExpiredScreen` opens with "Zeit ist um!".
6. Click OK → world resumes, HUD shows "Schlusszeit: 4:59" in red.
7. Wait 5 more minutes (or set the budget to 1 and grace effectively to test): hard timeout opens.
8. Click "Spiel beenden". World saves, Minecraft quits.
9. Re-launch, re-enter the same world: `BudgetQueryScreen` opens again (fresh-on-each-world-load confirmed).

### Step 5: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/budget/MinecraftBudgetSurface.java \
        src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java
git commit -m "Wire BudgetTracker + HUD into MatheaufgabenMod entrypoint"
```

---

## Task 6: README + CLAUDE.md updates

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

### Step 1: Add a "Play-budget timer" section to README.md

Insert after "## History log" and before "## Build":

```markdown
## Play-budget timer

On entering a world, the mod asks for a play-time budget in minutes (1–1440). A HUD in the
top-right shows the remaining time. When the budget runs out, a "Zeit ist um!" popup appears
that can be dismissed; the kid then has a 5-minute grace period (HUD turns red, counting down)
to reach a save point. After grace, a forced-quit popup appears with only a "Spiel beenden"
button — clicking it triggers Minecraft's normal save-and-quit. No data is lost.

The budget pauses automatically whenever the game is paused (game menu, math prompt, the
budget popups themselves). Leaving the world to title and re-entering re-prompts for a fresh
budget — there is no per-day cap in v1.
```

### Step 2: Add `budget/` package to CLAUDE.md Architecture

Append a new bullet AFTER the `history/` bullet (and the `achievements/` bullet, if that branch landed first) and BEFORE the `MatheaufgabenMod.java` bullet:

```markdown
- **`budget/`** — `BudgetTracker` runs a 5-state machine (WAITING_FOR_WORLD → WAITING_FOR_BUDGET
  → ACTIVE → EXPIRED → HARD_TIMEOUT) ticked from `ClientTickEvents.END_CLIENT_TICK`. The
  `BudgetSurface` interface is the test seam (same pattern as `ClientSurface` in `timer/`).
  Three Screen subclasses (`BudgetQueryScreen`, `BudgetSoftExpiredScreen`,
  `BudgetHardTimeoutScreen`) handle entry, soft expiry, and hard expiry; the last
  has only a "Spiel beenden" button calling `MinecraftClient.scheduleStop()` for graceful
  save & quit. `BudgetHudRenderer` registers a `HudRenderCallback` for the top-right
  Restzeit/Schlusszeit overlay. State is session-local — leaving the world resets it.
```

### Step 3: Mark the budget feature in Post-MVP TODOs

In `CLAUDE.md`'s "## Post-MVP TODOs" section, mark the budget-timer entry as shipped:

Old:
```markdown
- [ ] Configurable play-budget timer: allow Minecraft to be played with normal math-task settings for X minutes (configurable) before any math tasks kick in.
```

With:
```markdown
- [x] ~~Configurable play-budget timer: allow Minecraft to be played with normal math-task settings for X minutes (configurable) before any math tasks kick in.~~ Shipped: see `budget/` package and the "Play-budget timer" README section.
```

### Step 4: Run full test suite (sanity)

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: still 118 tests; no test changes here.

### Step 5: Commit

```sh
git add README.md CLAUDE.md
git commit -m "Document play-budget timer in README and CLAUDE.md"
```

---

## Final verification

- [ ] Full test suite green: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test` → expect 118 (89 baseline + 29 new: 11 + 9 + 4 + 5)
- [ ] Build clean: `./gradlew build` produces the jar with `budget/` classes inside
- [ ] Manual `runClient` smoke test (Task 5 Step 4) — human verifies the full flow: query → active → soft → grace → hard → quit
- [ ] `git log --oneline main..feat/play-budget-timer` shows 6 atomic commits + the plan-commit + maybe fix-ups

## Risk notes for execution

1. **Same-file conflict with feat/achievements**: both branches modify `MatheaufgabenMod.java` and `de_de.json`. Whichever lands second on `main` will conflict — likely trivially resolvable, but flag during merge. Spec/plan documents themselves are on disjoint paths.
2. **Wakeup behaviour**: opening a `Screen` via `client.setScreen` on every tick where we'd want one is fine — the call is idempotent if the same screen is already up, but in our state machine each open call happens at a one-shot transition so this doesn't fire repeatedly.
3. **HUD rendering during the title screen**: `HudRenderCallback` should not fire on the title screen because there's no game HUD context, but if it does, our `phase == WAITING_FOR_WORLD` check returns early. No visual leak.
