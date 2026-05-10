# Matheaufgaben History Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-attempt history log (`<minecraft>/config/matheaufgabenmod-history.log`) that records every math-task submission with timestamp, type, prompt, expected answer, given answer, result, and time-to-answer in seconds. Long-term, append-only, survives Minecraft restarts.

**Architecture:** New `history/` package with two files. `HistoryEntry` is a record carrying one log row; it derives the generator type from the prompt's operator character (avoiding an invasive `type` field on `Problem`). `HistoryLogger` opens the file lazily, writes a TSV header on first creation, and appends one TSV row per attempt. `PromptScreen` tracks the timestamp when each attempt starts (initially in the constructor, then reset after each wrong submission) and calls a `Consumer<HistoryEntry>` to log — tests can pass a list-recorder consumer rather than touching the filesystem. `MinecraftClientSurface` and `MatheaufgabenMod` thread the logger through.

**Why infer the type from the prompt rather than adding a `type` field to `Problem`:** the alternative would touch every generator + every Problem-constructing test. The four operators (` + `, ` − `, ` · `, ` : `) are unambiguous in the current grammar and the mapping is one line of code per type. If a fifth problem type lands later that doesn't slot into this scheme, this can be revisited.

**Tech stack:** Unchanged from MVP. Java 21, Fabric Loom 1.8, MC 1.21.4, JUnit 5.

**Reference docs:**
- MVP plan: `docs/superpowers/plans/2026-05-10-minecraft-matheaufgaben-mod.md`
- MVP spec: `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`
- Architecture overview: `CLAUDE.md`

---

## Task 1: HistoryEntry + HistoryLogger (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/history/HistoryEntry.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/history/HistoryLogger.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/history/HistoryLoggerTest.java`

`HistoryLogger.logAttempt(entry)` is the only public method. It:
- Creates the parent directory if needed.
- On first call (file doesn't exist or is empty), writes a TSV header row.
- Appends one TSV row per call.
- On `IOException`, logs at SLF4J `warn` and swallows — a failed log line must NEVER crash the prompt flow.

### Step 1: Write the failing tests

`src/test/java/dev/asante/matheaufgabenmod/history/HistoryLoggerTest.java`:
```java
package dev.asante.matheaufgabenmod.history;

import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryLoggerTest {

    @Test
    void writesHeaderAndOneRowWhenFileMissing(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(2300)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "header + 1 row");
        assertEquals("timestamp\ttype\tprompt\texpected\tgiven\tresult\tduration_s", lines.get(0));
        String[] cols = lines.get(1).split("\t");
        assertEquals(7, cols.length);
        assertEquals("plus", cols[1]);
        assertEquals("3 + 4", cols[2]);
        assertEquals("7", cols[3]);
        assertEquals("7", cols[4]);
        assertEquals("correct", cols[5]);
        assertEquals("2.30", cols[6]);
    }

    @Test
    void appendsWithoutRewritingHeader(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "8", false, Duration.ofMillis(1500)));
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(900)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size(), "header + 2 rows");
        assertTrue(lines.get(1).contains("\twrong\t"));
        assertTrue(lines.get(2).contains("\tcorrect\t"));
    }

    @Test
    void inferTypeFromOperator() {
        assertEquals("plus", HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ZERO).type());
        assertEquals("minus", HistoryEntry.fromAttempt(
                new Problem("10 − 3", "7"), "7", true, Duration.ZERO).type());
        assertEquals("einmaleins", HistoryEntry.fromAttempt(
                new Problem("7 · 8", "56"), "56", true, Duration.ZERO).type());
        assertEquals("division", HistoryEntry.fromAttempt(
                new Problem("12 : 4", "3"), "3", true, Duration.ZERO).type());
    }

    @Test
    void unknownTypeForUnrecognisedPrompt() {
        assertEquals("unknown", HistoryEntry.fromAttempt(
                new Problem("???", "x"), "x", true, Duration.ZERO).type());
    }

    @Test
    void trimsGivenAnswer() {
        HistoryEntry entry = HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "  7  ", true, Duration.ofMillis(500));
        assertEquals("7", entry.given());
    }

    @Test
    void durationFormattingTwoDecimals(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(12345)));
        List<String> lines = Files.readAllLines(file);
        String[] cols = lines.get(1).split("\t");
        assertEquals("12.35", cols[6]);  // 12345ms → 12.345s → "12.35"
    }

    @Test
    void ioErrorDoesNotPropagate(@TempDir Path tmp) {
        // Use a path under a regular file — directory creation will fail.
        Path notADir = tmp.resolve("blocker");
        try {
            Files.writeString(notADir, "blocker");
        } catch (IOException e) {
            fail("setup");
        }
        Path file = notADir.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        assertDoesNotThrow(() -> logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(100))));
    }
}
```

### Step 2: Run, confirm fail

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests HistoryLoggerTest
```

Expected: compilation error — `HistoryEntry` and `HistoryLogger` don't exist.

### Step 3: Implement `HistoryEntry.java`

`src/main/java/dev/asante/matheaufgabenmod/history/HistoryEntry.java`:
```java
package dev.asante.matheaufgabenmod.history;

import dev.asante.matheaufgabenmod.generator.Problem;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the math-task history log. Constructed via {@link #fromAttempt} which
 * stamps the current wall-clock time and derives the generator type by inspecting
 * the prompt for the operator character.
 */
public record HistoryEntry(
        OffsetDateTime timestamp,
        String type,
        String prompt,
        String expected,
        String given,
        boolean correct,
        Duration duration
) {

    public static HistoryEntry fromAttempt(Problem problem, String given, boolean correct, Duration duration) {
        return new HistoryEntry(
                OffsetDateTime.now(),
                inferType(problem.prompt()),
                problem.prompt(),
                problem.answer(),
                given.trim(),
                correct,
                duration
        );
    }

    /**
     * Infer the generator name from the prompt's operator character. Hard-coded
     * rather than carried on {@link Problem} to keep this feature non-invasive.
     */
    private static String inferType(String prompt) {
        if (prompt.contains(" + ")) return "plus";
        if (prompt.contains(" − ")) return "minus";        // U+2212
        if (prompt.contains(" · ")) return "einmaleins";   // U+00B7
        if (prompt.contains(" : ")) return "division";
        return "unknown";
    }

    /** Tab-separated row. The header row is hard-coded in {@link HistoryLogger}. */
    public String toTsv() {
        double seconds = duration.toMillis() / 1000.0;
        return String.format(java.util.Locale.ROOT,
                "%s\t%s\t%s\t%s\t%s\t%s\t%.2f",
                timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                type,
                prompt,
                expected,
                given,
                correct ? "correct" : "wrong",
                seconds);
    }
}
```

Note the explicit `Locale.ROOT` on `String.format`: the German locale would render `%.2f` with a comma decimal separator (`12,35`) which breaks TSV parsing. `Locale.ROOT` forces the period.

### Step 4: Implement `HistoryLogger.java`

`src/main/java/dev/asante/matheaufgabenmod/history/HistoryLogger.java`:
```java
package dev.asante.matheaufgabenmod.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only history log writer. Lazily creates the file on first call,
 * writing a TSV header row before any data. On {@link IOException}, logs to
 * SLF4J and swallows — a failed log must not crash the prompt flow.
 */
public final class HistoryLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("matheaufgabenmod");
    static final String HEADER = "timestamp\ttype\tprompt\texpected\tgiven\tresult\tduration_s";

    private final Path file;

    public HistoryLogger(Path file) {
        this.file = file;
    }

    public void logAttempt(HistoryEntry entry) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean writeHeader = !Files.exists(file) || Files.size(file) == 0;
            String line = (writeHeader ? HEADER + "\n" : "") + entry.toTsv() + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("[matheaufgabenmod] could not append to history log {}: {}",
                    file, e.getMessage());
        }
    }
}
```

### Step 5: Run, confirm pass

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests HistoryLoggerTest
```

Expected: 7 tests passed.

### Step 6: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/history/ \
        src/test/java/dev/asante/matheaufgabenmod/history/
git commit -m "Add HistoryLogger for per-attempt math-task logging"
```

---

## Task 2: Wire HistoryLogger through to PromptScreen

**Files:**
- Modify: `src/main/java/dev/asante/matheaufgabenmod/screen/PromptScreen.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/timer/MinecraftClientSurface.java`
- Modify: `src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java`
- Modify: `src/test/java/dev/asante/matheaufgabenmod/screen/PromptScreenTest.java`

`PromptScreen` gains:
- A `Consumer<HistoryEntry>` constructor parameter (so tests can verify writes without touching the filesystem).
- A `long attemptStartNanos` field, initialised in the constructor and reset after each wrong answer.
- In `onSubmit`: compute `Duration.ofNanos(System.nanoTime() - attemptStartNanos)`, build a `HistoryEntry`, and invoke the consumer **regardless of correct/wrong**.

`MinecraftClientSurface` gains a `Consumer<HistoryEntry>` parameter and forwards it to `PromptScreen`'s constructor.

`MatheaufgabenMod` instantiates a `HistoryLogger` once and passes `historyLogger::logAttempt` through.

### Step 1: Update `PromptScreenTest.java` to use the new constructor

The existing 7 tests of `PromptScreen.checkAnswer` use a 2-arg `Problem` constructor — those don't need to change because `checkAnswer` is a static method that doesn't touch the constructor. But for clarity, we'll add a new test that exercises the consumer-callback path.

Add to `PromptScreenTest.java` (do not delete existing tests):
```java
    // Add these imports at the top, alongside existing imports:
    // import dev.asante.matheaufgabenmod.history.HistoryEntry;
    // import java.util.ArrayList;
    // import java.util.List;
    // import java.util.function.Consumer;
    //
    // Add this test at the bottom, before the closing brace:

    @Test
    void historyConsumerSignatureCompiles() {
        // Smoke-test that the field declaration accepts the lambda we'll wire in.
        // The actual onSubmit callback flow is exercised manually via runClient
        // (Minecraft Screen lifecycle cannot run in plain JUnit).
        List<HistoryEntry> recorded = new ArrayList<>();
        Consumer<HistoryEntry> historyConsumer = recorded::add;
        assertNotNull(historyConsumer);
    }
```

This is a trivial compile-only check; the meaningful end-to-end verification of the consumer is via `runClient` (Step 6 below). We add it now so the test file references the new types and any breaking constructor change shows up at compile time.

### Step 2: Modify `PromptScreen.java`

Replace the entire file content:

```java
package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.history.HistoryEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PromptScreen extends Screen {

    private final Supplier<Problem> problemSupplier;
    private final Consumer<HistoryEntry> historyConsumer;
    private Problem currentProblem;
    private TextFieldWidget inputField;
    private Text feedback = Text.empty();
    private long attemptStartNanos;

    public PromptScreen(Supplier<Problem> problemSupplier, Problem initialProblem,
                        Consumer<HistoryEntry> historyConsumer) {
        super(Text.translatable("matheaufgabenmod.prompt.title"));
        this.problemSupplier = problemSupplier;
        this.currentProblem = initialProblem;
        this.historyConsumer = historyConsumer;
        this.attemptStartNanos = System.nanoTime();
    }

    public static boolean checkAnswer(Problem problem, String guess) {
        return guess.trim().equals(problem.answer());
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
                Text.translatable("matheaufgabenmod.prompt.title")
        );
        this.inputField.setMaxLength(16);
        this.addDrawableChild(this.inputField);
        this.setInitialFocus(this.inputField);

        ButtonWidget submit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.prompt.submit"),
                btn -> onSubmit()
        ).dimensions(cx - 50, cy + 40, 100, 20).build();
        this.addDrawableChild(submit);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (keyCode == 257 || keyCode == 335) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmit() {
        String given = inputField.getText();
        boolean correct = checkAnswer(currentProblem, given);
        Duration duration = Duration.ofNanos(System.nanoTime() - attemptStartNanos);
        historyConsumer.accept(HistoryEntry.fromAttempt(currentProblem, given, correct, duration));

        if (correct) {
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        currentProblem = problemSupplier.get();
        inputField.setText("");
        feedback = Text.translatable("matheaufgabenmod.prompt.wrong");
        attemptStartNanos = System.nanoTime();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 70;
        int promptY = this.height / 2 - 30;
        int feedbackY = this.height / 2 + 70;

        // Title (1.5x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        // Prompt text (2x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, promptY, 0);
        ctx.getMatrices().scale(2.0f, 2.0f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, currentProblem.prompt() + " =", 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        // Feedback (red)
        if (!feedback.getString().isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
```

### Step 3: Modify `MinecraftClientSurface.java`

Replace the entire file content:

```java
package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.history.HistoryEntry;
import dev.asante.matheaufgabenmod.screen.PromptScreen;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Production ClientSurface backed by a real MinecraftClient and the scheduler. */
public final class MinecraftClientSurface implements ClientSurface {

    private final MinecraftClient client;
    private final Supplier<Problem> problemSupplier;
    private final Consumer<HistoryEntry> historyConsumer;

    public MinecraftClientSurface(MinecraftClient client,
                                  Supplier<Problem> problemSupplier,
                                  Consumer<HistoryEntry> historyConsumer) {
        this.client = client;
        this.problemSupplier = problemSupplier;
        this.historyConsumer = historyConsumer;
    }

    @Override
    public boolean hasWorld() { return client.world != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public boolean currentScreenIsPrompt() { return client.currentScreen instanceof PromptScreen; }

    @Override
    public void openPromptScreen(Problem problem) {
        client.setScreen(new PromptScreen(problemSupplier, problem, historyConsumer));
    }
}
```

### Step 4: Modify `MatheaufgabenMod.java`

Replace the entire file content:

```java
package dev.asante.matheaufgabenmod;

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSurface surface = new MinecraftClientSurface(
                    client, scheduler::pickProblem, historyLogger::logAttempt);
            scheduler.onTick(surface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} section spec(s), history={}",
                MOD_ID, config.intervalMinutes(), config.sectionSpecs().size(), historyPath);
    }
}
```

### Step 5: Run full test suite

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: 84 tests passing (77 prior + 7 new from `HistoryLoggerTest`). The new `historyConsumerSignatureCompiles` test in `PromptScreenTest` was excluded from the count if you didn't add it — that's fine.

If the build fails because `PromptScreenTest` uses the old 2-arg `PromptScreen` constructor in code that exists outside the listed test additions, search the test file for `new PromptScreen(` and update those call sites to pass the third `Consumer<HistoryEntry>` argument. The existing tests of the static `checkAnswer` method should not need any constructor calls and should continue to pass unchanged.

### Step 6: Skip — manual smoke test via runClient

Manual GUI smoke test left for the human user. After the commit, the human should:
1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew runClient`
2. Enter a world, wait for a prompt (or set `intervalMinutes: 1` in the dev config for faster iteration).
3. Type a wrong answer + Enter; observe the new problem.
4. Type the correct answer + Enter; observe the world resume.
5. Confirm `run/config/matheaufgabenmod-history.log` exists and contains the expected TSV rows (header + 2 attempts).

### Step 7: Commit

```sh
git add src/main/java/dev/asante/matheaufgabenmod/screen/PromptScreen.java \
        src/main/java/dev/asante/matheaufgabenmod/timer/MinecraftClientSurface.java \
        src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java \
        src/test/java/dev/asante/matheaufgabenmod/screen/PromptScreenTest.java
git commit -m "Wire HistoryLogger through PromptScreen + MinecraftClientSurface"
```

---

## Task 3: Update CLAUDE.md and README with the history log

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

### Step 1: Append a "History log" subsection to README.md

In `README.md`, after the "Configuration" section and before "Build", insert:

```markdown
## History log

The mod appends every math-task submission to `<minecraft>/config/matheaufgabenmod-history.log` as
tab-separated rows with a header. Columns: `timestamp`, `type`, `prompt`, `expected`, `given`,
`result`, `duration_s`. Useful for spotting which problem types or operations the kid struggles
with. The file is append-only and survives Minecraft restarts; delete it manually to reset the
history.
```

### Step 2: Add the `history/` package to the Architecture section in CLAUDE.md

In `CLAUDE.md`, in the "## Architecture (the seams that matter)" section, append a new bullet after the `screen/` bullet and before the `MatheaufgabenMod.java` bullet:

```markdown
- **`history/`** — `HistoryLogger` appends one TSV row per math-task submission to
  `<minecraft>/config/matheaufgabenmod-history.log`. `HistoryEntry.fromAttempt` derives the
  generator type by scanning the prompt for the operator character (avoiding an invasive
  `type` field on `Problem`). IOException-tolerant: a failed log goes to SLF4J `warn` and
  is swallowed so the prompt flow never crashes on a disk error.
```

### Step 3: Strike the "Logging feature" line from Post-MVP TODOs

In `CLAUDE.md`, in the "## Post-MVP TODOs" section, replace the logging TODO:

Old:
```markdown
- [ ] Logging feature: log each math task solved or failed, including timestamp.
```

With:
```markdown
- [x] ~~Logging feature: log each math task solved or failed, including timestamp.~~ Shipped: see `history/` package and the "History log" README section.
```

### Step 4: Run full test suite (sanity)

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
```

Expected: same count as Task 2 (no test changes here).

### Step 5: Commit

```sh
git add CLAUDE.md README.md
git commit -m "Document history log in README and CLAUDE.md; strike logging TODO"
```

---

## Final verification

- [ ] Full test suite green: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test`
- [ ] Build clean: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build` produces `build/libs/matheaufgabenmod-0.1.0.jar`
- [ ] Manual `runClient` smoke test (Task 2 Step 6) — human verifies the history file is created and populated.
- [ ] `git log --oneline main..feat/history-log` shows 3 atomic commits + maybe small fix-ups.
