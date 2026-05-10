# Minecraft Matheaufgaben-Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric client-side mod for Minecraft Java Edition 1.21.x that interrupts singleplayer gameplay every X minutes with a math-problem prompt; the kid must answer correctly to resume.

**Architecture:** Layered Java packages with one clear responsibility each — `generator/` (pure functions of `(Random, Params) → List<Problem>` with no Minecraft dependency), `config/` (JSON file + `type:k=v,k=v` section-spec parser), `timer/` (tick-based scheduler that opens prompts at the configured interval), `screen/` (Minecraft `Screen` subclass that pauses singleplayer and refuses to close on Esc). The four generators (`plus`, `minus`, `einmaleins`, `division`) implement deterministic problem generation with explicit constraints (range, carry/borrow, factor rows, divisor sets). `MatheaufgabenMod` (`ClientModInitializer`) wires everything once at client startup.

**Tech Stack:** Java 21, Fabric Loom, Fabric Loader 0.16.x, Fabric API 0.110.x for MC 1.21.4, Gson (bundled), JUnit 5 for tests.

**Spec:** `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`

---

## Task 1: Project scaffolding (Fabric template + customisation)

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` (binary, via wrapper init)
- Create: `gradlew`, `gradlew.bat` (wrapper scripts, via wrapper init)
- Create: `.gitignore`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/matheaufgabenmod.mixins.json`
- Create: `src/main/resources/assets/matheaufgabenmod/lang/de_de.json`
- Create: `src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java` (placeholder; full impl in Task 13)

The Fabric example mod template at `https://github.com/FabricMC/fabric-example-mod` is the canonical starting shape. Rather than depend on the website's template generator, we transcribe the exact files needed. The placeholder `MatheaufgabenMod.java` exists so `runClient` succeeds before any logic is added.

- [ ] **Step 1: Initialise Gradle wrapper**

Run from the project root:
```sh
gradle wrapper --gradle-version 8.10 --distribution-type bin
```

If the host doesn't have a system `gradle`, install via the system package manager (`dnf install gradle` on Fedora) or download from `https://gradle.org/`. The wrapper init writes `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

- [ ] **Step 2: Write `gradle.properties`**

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

# Fabric Properties
# check these on https://fabricmc.net/develop
minecraft_version=1.21.4
yarn_mappings=1.21.4+build.1
loader_version=0.16.9

# Mod Properties
mod_version=0.1.0
maven_group=dev.asante
archives_base_name=matheaufgabenmod

# Dependencies
fabric_version=0.110.0+1.21.4
```

Note: the patch versions above are pinned to current values at time of writing. If `https://fabricmc.net/develop/` shows newer patch versions for MC 1.21.4 by the time you execute this plan, bump the three values (`yarn_mappings`, `loader_version`, `fabric_version`) to match. The major versions (`1.21.4`) stay fixed.

- [ ] **Step 3: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

- [ ] **Step 4: Write `build.gradle`**

```groovy
plugins {
    id 'fabric-loom' version '1.8-SNAPSHOT'
    id 'maven-publish'
    id 'java'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    // No additional repos needed; Loom adds Fabric and Mojang automatically.
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

processResources {
    inputs.property "version", project.version

    filesMatching("fabric.mod.json") {
        expand "version": inputs.properties.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 21
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

test {
    useJUnitPlatform()
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.archivesBaseName}" }
    }
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
        }
    }
    repositories {}
}
```

Note: the `LICENSE` file referenced in the `jar` block doesn't exist yet — that's fine, Gradle's `from(...)` silently skips missing files. Add a `LICENSE` (e.g. MIT) before publishing. For personal use this is irrelevant.

- [ ] **Step 5: Write `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
out/
classes/

# IntelliJ / Eclipse
.idea/
*.iml
*.ipr
*.iws
.classpath
.project
.settings/
bin/

# Fabric Loom
**/run/

# OS
.DS_Store
Thumbs.db
```

Note: `gradle/wrapper/gradle-wrapper.jar` is *not* ignored — the wrapper jar is committed.

- [ ] **Step 6: Write `src/main/resources/fabric.mod.json`**

```json
{
    "schemaVersion": 1,
    "id": "matheaufgabenmod",
    "version": "${version}",
    "name": "Matheaufgaben",
    "description": "Interrupts singleplayer Minecraft with math problems every X minutes.",
    "authors": ["Friedrich Wiemer"],
    "contact": {},
    "license": "MIT",
    "icon": "assets/matheaufgabenmod/icon.png",
    "environment": "client",
    "entrypoints": {
        "client": ["dev.asante.matheaufgabenmod.MatheaufgabenMod"]
    },
    "mixins": ["matheaufgabenmod.mixins.json"],
    "depends": {
        "fabricloader": ">=0.16",
        "minecraft": "~1.21.4",
        "java": ">=21",
        "fabric-api": "*"
    }
}
```

The `${version}` placeholder is filled in by the `processResources` block in `build.gradle`. The `icon.png` reference can be a 64×64 PNG; for v1, leave it absent (the client tolerates a missing icon with a console warning) or commit a one-pixel placeholder.

- [ ] **Step 7: Write `src/main/resources/matheaufgabenmod.mixins.json`**

```json
{
    "required": true,
    "package": "dev.asante.matheaufgabenmod.mixin",
    "compatibilityLevel": "JAVA_21",
    "client": [],
    "injectors": {
        "defaultRequire": 1
    }
}
```

We declare the mixins file because `fabric.mod.json` references it, but the `client` array is empty: v1 needs no mixins. Future tasks may add some; an empty file keeps the door open without imposing scope.

- [ ] **Step 8: Write `src/main/resources/assets/matheaufgabenmod/lang/de_de.json`**

```json
{
    "matheaufgabenmod.prompt.title": "Mathe-Aufgabe!",
    "matheaufgabenmod.prompt.submit": "Antworten",
    "matheaufgabenmod.prompt.wrong": "Falsch — versuch's nochmal"
}
```

- [ ] **Step 9: Write the `MatheaufgabenMod` placeholder**

```java
// src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java
package dev.asante.matheaufgabenmod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MatheaufgabenMod implements ClientModInitializer {

    public static final String MOD_ID = "matheaufgabenmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[matheaufgabenmod] initialising — placeholder, full wiring lands in Task 13");
    }
}
```

- [ ] **Step 10: Verify the build succeeds**

Run:
```sh
./gradlew build
```

Expected: BUILD SUCCESSFUL. The first run downloads Fabric Loom, Minecraft mappings, and dependencies — can take a few minutes on a fresh machine. Subsequent runs are seconds.

- [ ] **Step 11: Verify `runClient` boots**

Run:
```sh
./gradlew runClient
```

Expected: a Minecraft client window opens, title screen renders, the launcher console shows our `[matheaufgabenmod] initialising` log line. Quit via the title screen "Quit Game" button.

- [ ] **Step 12: Commit**

```sh
git add .
git commit -m "Add Fabric mod scaffolding (gradle, fabric.mod.json, lang, placeholder entrypoint)"
```

---

## Task 2: Problem record + Generator interface

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/Problem.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/Generator.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/ConfigException.java`

No tests yet — these are pure data/interface declarations consumed by later tasks.

- [ ] **Step 1: Write `Problem.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/Problem.java
package dev.asante.matheaufgabenmod.generator;

/**
 * Immutable math problem with its prompt and the canonical answer string.
 *
 * <p>Both fields are exactly what the player sees / types — no metadata, no
 * formatting indirection. {@code answer} is a string (not int) to handle
 * "3 R 1" remainder-form division answers and any future formats uniformly.
 */
public record Problem(String prompt, String answer) {}
```

- [ ] **Step 2: Write `Generator.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/Generator.java
package dev.asante.matheaufgabenmod.generator;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pure-function generator interface. Implementations have no Minecraft imports
 * and no I/O — they consume an {@link Random} and a generator-specific Params
 * record, and return a list of {@link Problem}s.
 */
public interface Generator {

    /** Registry key, e.g. "plus". */
    String name();

    /**
     * Validate and parse a section spec's raw key=value map into a Params
     * record specific to this generator. Throws {@link ConfigException} on
     * invalid input — the message must be prefixed with {@code "<name>: "}.
     */
    Object parseParams(Map<String, String> raw);

    /**
     * Generate {@code params.count}-many distinct problems using the supplied
     * RNG. Returns a list whose order depends on the RNG state.
     *
     * <p>Implementations narrow {@code params} from {@code Object} via an
     * {@code instanceof} check at the top of the method. The interface uses
     * {@code Object} (rather than a generic type parameter on {@link Generator})
     * because the {@link Registry} stores heterogeneous generator instances
     * under string keys; making the registry generic over the params type
     * would either erase to {@code Object} anyway or force callers to know
     * each concrete params type at the lookup site.
     */
    List<Problem> generate(Random rng, Object params);

    /** Human-readable schema, used for log diagnostics on bad config. */
    String describe();
}
```

- [ ] **Step 3: Write `ConfigException.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/ConfigException.java
package dev.asante.matheaufgabenmod.generator;

/**
 * Thrown when a section spec or generator parameter is invalid.
 *
 * <p>Runtime exception so generators don't pollute their signatures with
 * a checked throw. {@code ConfigLoader} catches it and falls back to defaults.
 */
public final class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Verify the build still passes**

Run:
```sh
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/
git commit -m "Add Problem record, Generator interface, ConfigException"
```

---

## Task 3: SectionSpec parser (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/config/SectionSpec.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/config/SectionSpecTest.java`

Parser for the section-spec mini-grammar `type:k=v[,k=v...]`. Empty body after the colon is allowed (zero params). Missing colon, empty type, malformed `k=v` (no `=`), empty key, and duplicate key all raise `ConfigException` with a descriptive message.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/config/SectionSpecTest.java
package dev.asante.matheaufgabenmod.config;

import dev.asante.matheaufgabenmod.generator.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectionSpecTest {

    @Test
    void parsesSimpleSpec() {
        SectionSpec s = SectionSpec.parse("plus:range=100,count=10");
        assertEquals("plus", s.type());
        assertEquals(Map.of("range", "100", "count", "10"), s.params());
    }

    @Test
    void parsesEmptyParamBody() {
        SectionSpec s = SectionSpec.parse("plus:");
        assertEquals("plus", s.type());
        assertEquals(Map.of(), s.params());
    }

    @Test
    void parsesSingleParam() {
        SectionSpec s = SectionSpec.parse("einmaleins:rows=2-9");
        assertEquals("einmaleins", s.type());
        assertEquals(Map.of("rows", "2-9"), s.params());
    }

    @Test
    void rejectsMissingColon() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus"));
        assertTrue(ex.getMessage().contains("missing ':'"), ex.getMessage());
    }

    @Test
    void rejectsEmptyType() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse(":range=100"));
        assertTrue(ex.getMessage().contains("empty type"), ex.getMessage());
    }

    @Test
    void rejectsMalformedKv() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:range=100,bogus"));
        assertTrue(ex.getMessage().contains("expected k=v"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateKey() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:range=100,range=20"));
        assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
    }

    @Test
    void rejectsEmptyKey() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:=100"));
        assertTrue(ex.getMessage().contains("empty key"), ex.getMessage());
    }
}
```

- [ ] **Step 2: Run the tests, confirm they fail**

Run:
```sh
./gradlew test --tests SectionSpecTest
```

Expected: compilation error — `SectionSpec` doesn't exist yet.

- [ ] **Step 3: Implement `SectionSpec.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/config/SectionSpec.java
package dev.asante.matheaufgabenmod.config;

import dev.asante.matheaufgabenmod.generator.ConfigException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed section spec of the form {@code type:k=v[,k=v...]}.
 *
 * <p>An empty body after the colon yields an empty params map. Missing colon,
 * empty type, malformed {@code k=v} pairs, empty keys, and duplicate keys all
 * raise {@link ConfigException} with a descriptive message.
 */
public record SectionSpec(String type, Map<String, String> params) {

    public static SectionSpec parse(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            throw new ConfigException("section spec missing ':' (got '" + spec + "')");
        }
        String type = spec.substring(0, colon);
        if (type.isEmpty()) {
            throw new ConfigException("section spec has empty type (got '" + spec + "')");
        }
        String body = spec.substring(colon + 1);
        Map<String, String> params = new LinkedHashMap<>();
        if (!body.isEmpty()) {
            for (String kv : body.split(",")) {
                int eq = kv.indexOf('=');
                if (eq < 0) {
                    throw new ConfigException(
                            "section '" + type + "': expected k=v, got '" + kv + "'");
                }
                String k = kv.substring(0, eq);
                String v = kv.substring(eq + 1);
                if (k.isEmpty()) {
                    throw new ConfigException(
                            "section '" + type + "': empty key in '" + kv + "'");
                }
                if (params.containsKey(k)) {
                    throw new ConfigException(
                            "section '" + type + "': duplicate key '" + k + "'");
                }
                params.put(k, v);
            }
        }
        return new SectionSpec(type, Map.copyOf(params));
    }
}
```

- [ ] **Step 4: Run the tests, confirm they pass**

Run:
```sh
./gradlew test --tests SectionSpecTest
```

Expected: 8 tests passed.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/config/SectionSpec.java \
        src/test/java/dev/asante/matheaufgabenmod/config/SectionSpecTest.java
git commit -m "Add SectionSpec parser (type:k=v,k=v)"
```

---

## Task 4: PlusGenerator (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/CarryMode.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/PlusGenerator.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/generator/PlusGeneratorTest.java`

Addition generator. Generates `count` distinct problems `a + b` on `0 ≤ a, b` with `a + b ≤ range`, optionally constrained to require or avoid carrying. The fail-fast contract: `parseParams` rejects impossible counts (e.g. asking for more carry-yes pairs than exist for a small range) **before** `generate` runs, by consulting an exact precomputed `CARRY_CAPACITY` table. Closed-form heuristics like `total/2` overestimate for small ranges and would let `generate` blow up at runtime — enumeration of the exact counts is the only sound approach for the four supported ranges.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/generator/PlusGeneratorTest.java
package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PlusGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) \\+ (\\d+)");

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static boolean hasCarry(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) + (b % 10) >= 10) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private final PlusGenerator gen = new PlusGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void rangeRespected() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(0 <= ab.a && ab.a <= 100);
            assertTrue(0 <= ab.b && ab.b <= 100);
            assertTrue(ab.a + ab.b <= 100);
        }
    }

    @Test
    void correctness() {
        Object p = gen.parseParams(Map.of("range", "1000", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a + ab.b), prob.answer());
        }
    }

    @Test
    void carryYes() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "carry", "yes"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(hasCarry(ab.a, ab.b), ab.a + " + " + ab.b + " should carry");
        }
    }

    @Test
    void carryNo() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "carry", "no"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertFalse(hasCarry(ab.a, ab.b), ab.a + " + " + ab.b + " should not carry");
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "50"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void countExceedsCapacityRaisesInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "9999", "carry", "no")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void carryYesTightCapacityCaughtInParseParams() {
        // range=10 carry=yes admits only 9 distinct (a, b) pairs (enumerated:
        // 1+9, 2+8, 2+9, 3+7, 3+8, 3+9, 4+6, 4+7, 4+8 — and stops there because
        // a+b must also be <= 10). parseParams must reject count=10 *before*
        // generate runs; a closed-form heuristic of total/2 = 33 would let
        // generate fail at runtime.
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "10", "carry", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsUnknownParam() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "bogus", "x")));
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void rejectsInvalidRange() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "50", "count", "5")));
        assertTrue(ex.getMessage().contains("range"));
    }

    @Test
    void rejectsInvalidCarry() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "carry", "maybe")));
        assertTrue(ex.getMessage().contains("carry"));
    }
}
```

- [ ] **Step 2: Run the tests, confirm they fail**

```sh
./gradlew test --tests PlusGeneratorTest
```

Expected: compilation error (`PlusGenerator` and `CarryMode` don't exist).

- [ ] **Step 3: Implement `CarryMode.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/CarryMode.java
package dev.asante.matheaufgabenmod.generator;

public enum CarryMode {
    YES, NO, MIXED;

    public static CarryMode parse(String raw) {
        return switch (raw) {
            case "yes" -> YES;
            case "no" -> NO;
            case "mixed" -> MIXED;
            default -> throw new ConfigException(
                    "plus: carry must be yes|no|mixed, got '" + raw + "'");
        };
    }
}
```

- [ ] **Step 4: Implement `PlusGenerator.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/PlusGenerator.java
package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class PlusGenerator implements Generator {

    public static final int[] VALID_RANGES = {10, 20, 100, 1000};
    private static final Set<String> KNOWN_PARAMS = Set.of("range", "count", "carry");

    public record PlusParams(int range, int count, CarryMode carry) {}

    private record CarryKey(int range, CarryMode mode) {}

    /**
     * Exact (a, b) pair-count table per (range, carry mode), populated at class load.
     * The mixed-mode capacity is closed-form ((n+1)(n+2)/2) and computed inline; the
     * yes/no modes need enumeration because closed-form heuristics overestimate for
     * small ranges and break the fail-fast contract.
     */
    private static final Map<CarryKey, Integer> CARRY_CAPACITY = computeCarryCapacities();

    @Override
    public String name() { return "plus"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("plus: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("range")) throw new ConfigException("plus: missing required param 'range'");
        if (!raw.containsKey("count")) throw new ConfigException("plus: missing required param 'count'");
        int range;
        try {
            range = Integer.parseInt(raw.get("range"));
        } catch (NumberFormatException e) {
            throw new ConfigException("plus: range must be int, got '" + raw.get("range") + "'");
        }
        boolean validRange = false;
        for (int r : VALID_RANGES) if (r == range) { validRange = true; break; }
        if (!validRange) {
            throw new ConfigException(
                    "plus: range must be one of " + Arrays.toString(VALID_RANGES) + ", got " + range);
        }
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("plus: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("plus: count must be >= 1, got " + count);
        CarryMode carry = raw.containsKey("carry") ? CarryMode.parse(raw.get("carry")) : CarryMode.MIXED;
        PlusParams params = new PlusParams(range, count, carry);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "plus: count=" + count + " exceeds capacity " + capacity
                            + " for range=" + range + ", carry=" + carry.name().toLowerCase());
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof PlusParams params)) {
            throw new IllegalArgumentException("plus: expected PlusParams, got " + paramsObj);
        }
        Set<Long> seen = new HashSet<>();
        List<Problem> problems = new ArrayList<>(params.count);
        int maxAttempts = Math.max(1000, 200 * params.count);
        for (int i = 0; i < maxAttempts && problems.size() < params.count; i++) {
            int a = rng.nextInt(params.range + 1);
            int b = rng.nextInt(params.range + 1 - a);
            long key = ((long) a << 32) | b;
            if (!seen.add(key)) continue;
            if (!carryMatches(a, b, params.carry)) continue;
            problems.add(new Problem(a + " + " + b, Integer.toString(a + b)));
        }
        if (problems.size() < params.count) {
            throw new ConfigException(
                    "plus: could not generate " + params.count + " unique problems "
                            + "under constraints (got " + problems.size() + ")");
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                plus — addition
                  range: 10|20|100|1000  (required)
                  count: int             (required, >=1)
                  carry: yes|no|mixed    (default mixed)
                """;
    }

    private static boolean hasCarry(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) + (b % 10) >= 10) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private static boolean carryMatches(int a, int b, CarryMode mode) {
        if (mode == CarryMode.MIXED) return true;
        boolean has = hasCarry(a, b);
        return mode == CarryMode.YES ? has : !has;
    }

    private static int capacity(PlusParams params) {
        int n = params.range;
        if (params.carry == CarryMode.MIXED) {
            return (n + 1) * (n + 2) / 2;
        }
        return CARRY_CAPACITY.get(new CarryKey(n, params.carry));
    }

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
}
```

- [ ] **Step 5: Run the tests, confirm they pass**

```sh
./gradlew test --tests PlusGeneratorTest
```

Expected: 12 tests passed.

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/CarryMode.java \
        src/main/java/dev/asante/matheaufgabenmod/generator/PlusGenerator.java \
        src/test/java/dev/asante/matheaufgabenmod/generator/PlusGeneratorTest.java
git commit -m "Add plus generator with carry/range/uniqueness constraints"
```

---

## Task 5: MinusGenerator (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/BorrowMode.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/MinusGenerator.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/generator/MinusGeneratorTest.java`

Subtraction generator. Generates `count` distinct problems `a − b` (using U+2212 MINUS SIGN, not ASCII hyphen) on `0 ≤ a, b ≤ range`, optionally constraining whether borrowing is required (digit-wise) and whether negative results are allowed. Same fail-fast capacity contract as the addition generator: an exact `BORROW_CAPACITY` table keyed by `(range, borrow mode, negative results)` is consulted in `parseParams`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/generator/MinusGeneratorTest.java
package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MinusGeneratorTest {

    // U+2212 MINUS SIGN, not ASCII hyphen.
    private static final Pattern PROMPT = Pattern.compile("(\\d+) − (\\d+)");

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static boolean hasBorrow(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) < (b % 10)) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private final MinusGenerator gen = new MinusGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void nonNegativeByDefault() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(ab.a >= ab.b);
            assertEquals(Integer.toString(ab.a - ab.b), prob.answer());
        }
    }

    @Test
    void rangeRespected() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(0 <= ab.a && ab.a <= 100);
            assertTrue(0 <= ab.b && ab.b <= 100);
        }
    }

    @Test
    void borrowYes() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "borrow", "yes"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(hasBorrow(ab.a, ab.b));
        }
    }

    @Test
    void borrowNo() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "borrow", "no"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertFalse(hasBorrow(ab.a, ab.b));
        }
    }

    @Test
    void negativeResultsAllowed() {
        Object p = gen.parseParams(
                Map.of("range", "100", "count", "30", "negative_results", "true"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a - ab.b), prob.answer());
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "50"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaisesInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "9999", "borrow", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void borrowYesTightCapacityCaughtInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "100", "borrow", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidRange() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "50", "count", "5")));
        assertTrue(ex.getMessage().contains("range"));
    }

    @Test
    void rejectsInvalidBorrow() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "borrow", "maybe")));
        assertTrue(ex.getMessage().contains("borrow"));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests MinusGeneratorTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `BorrowMode.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/BorrowMode.java
package dev.asante.matheaufgabenmod.generator;

public enum BorrowMode {
    YES, NO, MIXED;

    public static BorrowMode parse(String raw) {
        return switch (raw) {
            case "yes" -> YES;
            case "no" -> NO;
            case "mixed" -> MIXED;
            default -> throw new ConfigException(
                    "minus: borrow must be yes|no|mixed, got '" + raw + "'");
        };
    }
}
```

- [ ] **Step 4: Implement `MinusGenerator.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/MinusGenerator.java
package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class MinusGenerator implements Generator {

    public static final int[] VALID_RANGES = {10, 20, 100, 1000};
    private static final Set<String> KNOWN_PARAMS =
            Set.of("range", "count", "borrow", "negative_results");

    private static final String MINUS_SIGN = "−";  // U+2212

    public record MinusParams(int range, int count, BorrowMode borrow, boolean negativeResults) {}

    private record BorrowKey(int range, BorrowMode mode, boolean negativeResults) {}

    private static final Map<BorrowKey, Integer> BORROW_CAPACITY = computeBorrowCapacities();

    @Override
    public String name() { return "minus"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("minus: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("range")) throw new ConfigException("minus: missing required param 'range'");
        if (!raw.containsKey("count")) throw new ConfigException("minus: missing required param 'count'");
        int range;
        try {
            range = Integer.parseInt(raw.get("range"));
        } catch (NumberFormatException e) {
            throw new ConfigException("minus: range must be int, got '" + raw.get("range") + "'");
        }
        boolean validRange = false;
        for (int r : VALID_RANGES) if (r == range) { validRange = true; break; }
        if (!validRange) {
            throw new ConfigException(
                    "minus: range must be one of " + Arrays.toString(VALID_RANGES) + ", got " + range);
        }
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("minus: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("minus: count must be >= 1, got " + count);
        BorrowMode borrow = raw.containsKey("borrow") ? BorrowMode.parse(raw.get("borrow")) : BorrowMode.MIXED;
        boolean neg = parseBool(raw.getOrDefault("negative_results", "false"), "negative_results");
        MinusParams params = new MinusParams(range, count, borrow, neg);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "minus: count=" + count + " exceeds capacity " + capacity
                            + " for range=" + range + ", borrow=" + borrow.name().toLowerCase());
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof MinusParams params)) {
            throw new IllegalArgumentException("minus: expected MinusParams, got " + paramsObj);
        }
        Set<Long> seen = new HashSet<>();
        List<Problem> problems = new ArrayList<>(params.count);
        int maxAttempts = Math.max(1000, 200 * params.count);
        for (int i = 0; i < maxAttempts && problems.size() < params.count; i++) {
            int a = rng.nextInt(params.range + 1);
            int bUpper = params.negativeResults ? params.range : a;
            int b = rng.nextInt(bUpper + 1);
            if (!params.negativeResults && a < b) continue;
            long key = ((long) a << 32) | b;
            if (!seen.add(key)) continue;
            if (!borrowMatches(a, b, params.borrow)) continue;
            problems.add(new Problem(a + " " + MINUS_SIGN + " " + b, Integer.toString(a - b)));
        }
        if (problems.size() < params.count) {
            throw new ConfigException(
                    "minus: could not generate " + params.count + " unique problems "
                            + "under constraints (got " + problems.size() + ")");
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                minus — subtraction
                  range: 10|20|100|1000     (required)
                  count: int                (required, >=1)
                  borrow: yes|no|mixed      (default mixed)
                  negative_results: bool    (default false; if false, a >= b)
                """;
    }

    private static boolean parseBool(String raw, String name) {
        String lower = raw.toLowerCase();
        if (lower.equals("true") || lower.equals("yes") || lower.equals("1")) return true;
        if (lower.equals("false") || lower.equals("no") || lower.equals("0")) return false;
        throw new ConfigException("minus: " + name + " must be true|false, got '" + raw + "'");
    }

    private static boolean hasBorrow(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) < (b % 10)) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private static boolean borrowMatches(int a, int b, BorrowMode mode) {
        if (mode == BorrowMode.MIXED) return true;
        boolean has = hasBorrow(a, b);
        return mode == BorrowMode.YES ? has : !has;
    }

    private static int capacity(MinusParams params) {
        int n = params.range;
        if (params.borrow == BorrowMode.MIXED) {
            return params.negativeResults ? (n + 1) * (n + 1) : (n + 1) * (n + 2) / 2;
        }
        return BORROW_CAPACITY.get(new BorrowKey(n, params.borrow, params.negativeResults));
    }

    private static Map<BorrowKey, Integer> computeBorrowCapacities() {
        Map<BorrowKey, Integer> out = new HashMap<>();
        for (int r : VALID_RANGES) {
            for (boolean neg : new boolean[]{false, true}) {
                int yes = 0, no = 0;
                int aMax = r;
                for (int a = 0; a <= aMax; a++) {
                    int bMax = neg ? r : a;
                    for (int b = 0; b <= bMax; b++) {
                        if (hasBorrow(a, b)) yes++; else no++;
                    }
                }
                out.put(new BorrowKey(r, BorrowMode.YES, neg), yes);
                out.put(new BorrowKey(r, BorrowMode.NO, neg), no);
            }
        }
        return out;
    }
}
```

- [ ] **Step 5: Run, confirm pass**

```sh
./gradlew test --tests MinusGeneratorTest
```

Expected: 12 tests passed.

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/BorrowMode.java \
        src/main/java/dev/asante/matheaufgabenmod/generator/MinusGenerator.java \
        src/test/java/dev/asante/matheaufgabenmod/generator/MinusGeneratorTest.java
git commit -m "Add minus generator with borrow/negative_results constraints"
```

---

## Task 6: EinmaleinsGenerator (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGenerator.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGeneratorTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGeneratorTest.java
package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class EinmaleinsGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) · (\\d+)");  // U+00B7 middle dot

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private final EinmaleinsGenerator gen = new EinmaleinsGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void correctness() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a * ab.b), prob.answer());
        }
    }

    @Test
    void factorConstraintRange() {
        Object p = gen.parseParams(Map.of("rows", "2-5", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(Set.of(2, 3, 4, 5).contains(ab.a));
            assertTrue(1 <= ab.b && ab.b <= 10);
        }
    }

    @Test
    void factorConstraintList() {
        Object p = gen.parseParams(Map.of("rows", "3,7", "count", "20"));
        Set<Integer> rowsSeen = new HashSet<>();
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            rowsSeen.add(ab.a);
        }
        assertTrue(Set.of(3, 7).containsAll(rowsSeen));
    }

    @Test
    void rowsAll() {
        Object p = gen.parseParams(Map.of("rows", "all", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(1 <= ab.a && ab.a <= 10);
            assertTrue(1 <= ab.b && ab.b <= 10);
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "40"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void distinctOrderings() {
        Object p = gen.parseParams(Map.of("rows", "7", "count", "10"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(7, ab.a);
        }
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "20"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaises() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("rows", "3", "count", "11")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidRows() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("rows", "x-y", "count", "5")));
        assertTrue(ex.getMessage().contains("rows"));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests EinmaleinsGeneratorTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `EinmaleinsGenerator.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGenerator.java
package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class EinmaleinsGenerator implements Generator {

    private static final Set<String> KNOWN_PARAMS = Set.of("rows", "count");
    private static final String MULT_SIGN = "·";  // U+00B7 MIDDLE DOT

    public record EinmaleinsParams(int[] rows, int count) {}

    @Override
    public String name() { return "einmaleins"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("einmaleins: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("rows")) throw new ConfigException("einmaleins: missing required param 'rows'");
        if (!raw.containsKey("count")) throw new ConfigException("einmaleins: missing required param 'count'");
        int[] rows = parseRows(raw.get("rows"));
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("einmaleins: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("einmaleins: count must be >= 1, got " + count);
        int capacity = rows.length * 10;
        if (count > capacity) {
            throw new ConfigException(
                    "einmaleins: count=" + count + " exceeds capacity " + capacity
                            + " for rows=" + raw.get("rows"));
        }
        return new EinmaleinsParams(rows, count);
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof EinmaleinsParams params)) {
            throw new IllegalArgumentException("einmaleins: expected EinmaleinsParams, got " + paramsObj);
        }
        List<int[]> allPairs = new ArrayList<>();
        for (int r : params.rows) {
            for (int j = 1; j <= 10; j++) {
                allPairs.add(new int[]{r, j});
            }
        }
        Collections.shuffle(allPairs, rng);
        List<Problem> problems = new ArrayList<>(params.count);
        for (int i = 0; i < params.count; i++) {
            int a = allPairs.get(i)[0];
            int b = allPairs.get(i)[1];
            problems.add(new Problem(a + " " + MULT_SIGN + " " + b, Integer.toString(a * b)));
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                einmaleins — multiplication tables (1..10 × rows)
                  rows: range like '2-9' | list like '2,3,7' | 'all'  (required)
                  count: int                                          (required, >=1)
                """;
    }

    private static int[] parseRows(String raw) {
        String s = raw.strip();
        if (s.equals("all")) return new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        if (s.contains("-")) {
            String[] parts = s.split("-");
            if (parts.length != 2) {
                throw new ConfigException("einmaleins: rows malformed range '" + raw + "'");
            }
            int start, end;
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ConfigException("einmaleins: rows range must be ints, got '" + raw + "'");
            }
            if (!(1 <= start && start <= end && end <= 10)) {
                throw new ConfigException(
                        "einmaleins: rows must satisfy 1<=start<=end<=10, got '" + raw + "'");
            }
            int[] out = new int[end - start + 1];
            for (int i = 0; i < out.length; i++) out[i] = start + i;
            return out;
        }
        // Comma-separated list
        String[] parts = s.split(",");
        Set<Integer> seen = new LinkedHashSet<>();
        for (String part : parts) {
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new ConfigException(
                        "einmaleins: rows list must be comma-separated ints, got '" + raw + "'");
            }
            if (v < 1 || v > 10) {
                throw new ConfigException(
                        "einmaleins: rows entries must be 1..10, got '" + raw + "'");
            }
            seen.add(v);
        }
        if (seen.isEmpty()) {
            throw new ConfigException("einmaleins: rows list is empty");
        }
        int[] out = new int[seen.size()];
        int i = 0;
        for (int v : seen) out[i++] = v;
        return out;
    }
}
```

- [ ] **Step 4: Run, confirm pass**

```sh
./gradlew test --tests EinmaleinsGeneratorTest
```

Expected: 10 tests passed.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGenerator.java \
        src/test/java/dev/asante/matheaufgabenmod/generator/EinmaleinsGeneratorTest.java
git commit -m "Add einmaleins generator (multiplication tables)"
```

---

## Task 7: DivisionGenerator (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/DivisionGenerator.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/generator/DivisionGeneratorTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/generator/DivisionGeneratorTest.java
package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DivisionGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) : (\\d+)");
    private static final Pattern ANSWER = Pattern.compile("(\\d+)(?: R (\\d+))?");

    private record AB(int a, int b) {}
    private record QR(int q, int r) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static QR parseAnswer(String answer) {
        Matcher m = ANSWER.matcher(answer);
        assertTrue(m.matches(), "unexpected answer: " + answer);
        int q = Integer.parseInt(m.group(1));
        int r = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        return new QR(q, r);
    }

    private final DivisionGenerator gen = new DivisionGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void cleanWhenNoRemainder() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            QR qr = parseAnswer(prob.answer());
            assertEquals(0, qr.r);
            assertEquals(ab.a, ab.b * qr.q);
        }
    }

    @Test
    void withRemainderConsistent() {
        Object p = gen.parseParams(
                Map.of("divisor", "2-9", "count", "30", "with_remainder", "true"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            QR qr = parseAnswer(prob.answer());
            assertTrue(0 <= qr.r && qr.r < ab.b);
            assertEquals(ab.a, ab.b * qr.q + qr.r);
        }
    }

    @Test
    void divisorInRange() {
        Object p = gen.parseParams(Map.of("divisor", "3-5", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(Set.of(3, 4, 5).contains(ab.b));
        }
    }

    @Test
    void quotientBoundedByResultMax() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "20", "result_max", "5"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            QR qr = parseAnswer(prob.answer());
            assertTrue(1 <= qr.q && qr.q <= 5);
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "40"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "20"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaises() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("divisor", "2", "count", "10", "result_max", "3")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidDivisor() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("divisor", "0-9", "count", "5")));
        assertTrue(ex.getMessage().contains("divisor"));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests DivisionGeneratorTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `DivisionGenerator.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/DivisionGenerator.java
package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DivisionGenerator implements Generator {

    private static final Set<String> KNOWN_PARAMS =
            Set.of("divisor", "count", "with_remainder", "result_max");
    private static final String DIV_SIGN = ":";

    public record DivisionParams(int[] divisors, int count, boolean withRemainder, int resultMax) {}

    @Override
    public String name() { return "division"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("division: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("divisor")) throw new ConfigException("division: missing required param 'divisor'");
        if (!raw.containsKey("count")) throw new ConfigException("division: missing required param 'count'");
        int[] divisors = parseDivisor(raw.get("divisor"));
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("division: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("division: count must be >= 1, got " + count);
        boolean withRemainder = parseBool(raw.getOrDefault("with_remainder", "false"), "with_remainder");
        int resultMax;
        try {
            resultMax = Integer.parseInt(raw.getOrDefault("result_max", "10"));
        } catch (NumberFormatException e) {
            throw new ConfigException("division: result_max must be int, got '" + raw.get("result_max") + "'");
        }
        if (resultMax < 1) throw new ConfigException("division: result_max must be >= 1, got " + resultMax);
        DivisionParams params = new DivisionParams(divisors, count, withRemainder, resultMax);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "division: count=" + count + " exceeds capacity " + capacity
                            + " for divisor=" + raw.get("divisor")
                            + ", result_max=" + resultMax + ", with_remainder=" + withRemainder);
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof DivisionParams params)) {
            throw new IllegalArgumentException("division: expected DivisionParams, got " + paramsObj);
        }
        List<int[]> all = new ArrayList<>();  // (d, q, r)
        for (int d : params.divisors) {
            for (int q = 1; q <= params.resultMax; q++) {
                if (params.withRemainder) {
                    for (int r = 0; r < d; r++) {
                        all.add(new int[]{d, q, r});
                    }
                } else {
                    all.add(new int[]{d, q, 0});
                }
            }
        }
        Collections.shuffle(all, rng);
        List<Problem> problems = new ArrayList<>(params.count);
        for (int i = 0; i < params.count; i++) {
            int d = all.get(i)[0];
            int q = all.get(i)[1];
            int r = all.get(i)[2];
            int dividend = d * q + r;
            String answer = r == 0 ? Integer.toString(q) : q + " R " + r;
            problems.add(new Problem(dividend + " " + DIV_SIGN + " " + d, answer));
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                division — basic division
                  divisor: range '2-9' | list '2,3' | single '7'  (required, all values >=1)
                  count: int                                       (required, >=1)
                  with_remainder: bool                             (default false)
                  result_max: int                                  (default 10; cap on quotient)
                """;
    }

    private static boolean parseBool(String raw, String name) {
        String lower = raw.toLowerCase();
        if (lower.equals("true") || lower.equals("yes") || lower.equals("1")) return true;
        if (lower.equals("false") || lower.equals("no") || lower.equals("0")) return false;
        throw new ConfigException("division: " + name + " must be true|false, got '" + raw + "'");
    }

    private static int[] parseDivisor(String raw) {
        String s = raw.strip();
        if (s.contains("-")) {
            String[] parts = s.split("-");
            if (parts.length != 2) {
                throw new ConfigException("division: divisor malformed range '" + raw + "'");
            }
            int start, end;
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ConfigException("division: divisor range must be ints, got '" + raw + "'");
            }
            if (start < 1 || end < start) {
                throw new ConfigException(
                        "division: divisor must satisfy 1<=start<=end, got '" + raw + "'");
            }
            int[] out = new int[end - start + 1];
            for (int i = 0; i < out.length; i++) out[i] = start + i;
            return out;
        }
        String[] parts = s.split(",");
        Set<Integer> seen = new LinkedHashSet<>();
        for (String part : parts) {
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new ConfigException(
                        "division: divisor must be int, range, or comma list, got '" + raw + "'");
            }
            if (v < 1) {
                throw new ConfigException(
                        "division: divisor entries must be >=1, got '" + raw + "'");
            }
            seen.add(v);
        }
        if (seen.isEmpty()) {
            throw new ConfigException("division: divisor list is empty");
        }
        int[] out = new int[seen.size()];
        int i = 0;
        for (int v : seen) out[i++] = v;
        return out;
    }

    private static int capacity(DivisionParams params) {
        if (params.withRemainder) {
            int sum = 0;
            for (int d : params.divisors) sum += d * params.resultMax;
            return sum;
        }
        return params.divisors.length * params.resultMax;
    }
}
```

- [ ] **Step 4: Run, confirm pass**

```sh
./gradlew test --tests DivisionGeneratorTest
```

Expected: 9 tests passed.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/DivisionGenerator.java \
        src/test/java/dev/asante/matheaufgabenmod/generator/DivisionGeneratorTest.java
git commit -m "Add division generator with optional remainder support"
```

---

## Task 8: Generator Registry

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/generator/Registry.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/generator/RegistryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/generator/RegistryTest.java
package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    @Test
    void allFourGeneratorsRegistered() {
        assertTrue(Registry.contains("plus"));
        assertTrue(Registry.contains("minus"));
        assertTrue(Registry.contains("einmaleins"));
        assertTrue(Registry.contains("division"));
    }

    @Test
    void getReturnsRegisteredGenerator() {
        Generator g = Registry.get("plus");
        assertEquals("plus", g.name());
    }

    @Test
    void getUnknownThrows() {
        ConfigException ex = assertThrows(ConfigException.class, () -> Registry.get("addition"));
        assertTrue(ex.getMessage().contains("unknown problem type 'addition'"));
    }

    @Test
    void allNamesReturnsSortedKeys() {
        assertEquals(java.util.List.of("division", "einmaleins", "minus", "plus"),
                Registry.allNames());
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests RegistryTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `Registry.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/generator/Registry.java
package dev.asante.matheaufgabenmod.generator;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Registry {

    private static final Map<String, Generator> GENERATORS;

    static {
        Map<String, Generator> map = new TreeMap<>();
        registerInto(map, new PlusGenerator());
        registerInto(map, new MinusGenerator());
        registerInto(map, new EinmaleinsGenerator());
        registerInto(map, new DivisionGenerator());
        GENERATORS = Map.copyOf(map);
    }

    private static void registerInto(Map<String, Generator> map, Generator gen) {
        if (map.containsKey(gen.name())) {
            throw new IllegalStateException("generator '" + gen.name() + "' already registered");
        }
        map.put(gen.name(), gen);
    }

    private Registry() {}

    public static boolean contains(String name) {
        return GENERATORS.containsKey(name);
    }

    public static Generator get(String name) {
        Generator g = GENERATORS.get(name);
        if (g == null) {
            throw new ConfigException("unknown problem type '" + name + "'");
        }
        return g;
    }

    public static List<String> allNames() {
        return List.copyOf(GENERATORS.keySet());
    }
}
```

- [ ] **Step 4: Run, confirm pass**

```sh
./gradlew test --tests RegistryTest
```

Expected: 4 tests passed.

- [ ] **Step 5: Run full test suite**

```sh
./gradlew test
```

Expected: 55 tests passed (8 SectionSpec + 12 plus + 12 minus + 10 einmaleins + 9 division + 4 registry).

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/generator/Registry.java \
        src/test/java/dev/asante/matheaufgabenmod/generator/RegistryTest.java
git commit -m "Add generator Registry with the four MVP generators"
```

---

## Task 9: ModConfig record + ConfigLoader (TDD)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/config/ModConfig.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/config/ConfigLoader.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/config/ConfigLoaderTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/config/ConfigLoaderTest.java
package dev.asante.matheaufgabenmod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadsDefaultsWhenFileMissing(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT, cfg);
        assertTrue(Files.exists(configFile), "should create the config file with defaults");
    }

    @Test
    void loadsValidConfig(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {
                  "intervalMinutes": 7,
                  "sectionSpecs": ["plus:range=20,count=1"]
                }
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(7, cfg.intervalMinutes());
        assertEquals(List.of("plus:range=20,count=1"), cfg.sectionSpecs());
    }

    @Test
    void fallsBackToDefaultsOnMalformedJson(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, "not json {{");
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT, cfg);
    }

    @Test
    void dropsInvalidSpecsAndKeepsValidOnes(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {
                  "intervalMinutes": 5,
                  "sectionSpecs": [
                    "plus:range=100,count=1",
                    "garbage_no_colon",
                    "minus:range=100,count=1"
                  ]
                }
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(2, cfg.sectionSpecs().size());
        assertTrue(cfg.sectionSpecs().contains("plus:range=100,count=1"));
        assertTrue(cfg.sectionSpecs().contains("minus:range=100,count=1"));
    }

    @Test
    void fallsBackToDefaultsWhenAllSpecsInvalid(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {"intervalMinutes": 5, "sectionSpecs": ["garbage", "more_garbage"]}
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT.sectionSpecs(), cfg.sectionSpecs());
    }

    @Test
    void rejectsIntervalLessThanOne(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {"intervalMinutes": 0, "sectionSpecs": ["plus:range=10,count=1"]}
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT.intervalMinutes(), cfg.intervalMinutes());
    }

    @Test
    void modConfigDefaultIsValid() {
        // Each default spec should parse successfully.
        for (String spec : ModConfig.DEFAULT.sectionSpecs()) {
            assertDoesNotThrow(() -> SectionSpec.parse(spec),
                    "default spec must parse: " + spec);
        }
        assertTrue(ModConfig.DEFAULT.intervalMinutes() >= 1);
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests ConfigLoaderTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `ModConfig.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/config/ModConfig.java
package dev.asante.matheaufgabenmod.config;

import java.util.List;

public record ModConfig(int intervalMinutes, List<String> sectionSpecs) {

    public static final ModConfig DEFAULT = new ModConfig(
            5,
            List.of(
                    "plus:range=100,count=1,carry=mixed",
                    "minus:range=100,count=1,borrow=mixed",
                    "einmaleins:rows=2-9,count=1",
                    "division:divisor=2-9,count=1"
            )
    );
}
```

- [ ] **Step 4: Implement `ConfigLoader.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/config/ConfigLoader.java
package dev.asante.matheaufgabenmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.asante.matheaufgabenmod.generator.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("matheaufgabenmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {}

    /**
     * Load the config file at {@code path}, creating it with defaults if absent.
     * Falls back to defaults on any parse error rather than disabling the mod.
     */
    public static ModConfig loadOrCreate(Path path) {
        if (!Files.exists(path)) {
            writeDefaults(path);
            return ModConfig.DEFAULT;
        }
        try {
            String text = Files.readString(path);
            RawConfig raw = GSON.fromJson(text, RawConfig.class);
            if (raw == null) {
                LOGGER.error("[matheaufgabenmod] config file empty; using defaults");
                return ModConfig.DEFAULT;
            }
            return validate(raw);
        } catch (JsonSyntaxException e) {
            LOGGER.error("[matheaufgabenmod] config file is not valid JSON: {}; using defaults", e.getMessage());
            return ModConfig.DEFAULT;
        } catch (IOException e) {
            LOGGER.error("[matheaufgabenmod] failed to read config: {}; using defaults", e.getMessage());
            return ModConfig.DEFAULT;
        }
    }

    private static void writeDefaults(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(ModConfig.DEFAULT));
            LOGGER.info("[matheaufgabenmod] wrote default config to {}", path);
        } catch (IOException e) {
            LOGGER.error("[matheaufgabenmod] could not write default config: {}", e.getMessage());
        }
    }

    private static ModConfig validate(RawConfig raw) {
        int interval = raw.intervalMinutes;
        if (interval < 1) {
            LOGGER.warn("[matheaufgabenmod] intervalMinutes={} invalid; using default {}",
                    interval, ModConfig.DEFAULT.intervalMinutes());
            interval = ModConfig.DEFAULT.intervalMinutes();
        }
        List<String> validSpecs = new ArrayList<>();
        if (raw.sectionSpecs != null) {
            for (String spec : raw.sectionSpecs) {
                try {
                    SectionSpec.parse(spec);
                    validSpecs.add(spec);
                } catch (ConfigException e) {
                    LOGGER.warn("[matheaufgabenmod] dropping invalid section spec '{}': {}",
                            spec, e.getMessage());
                }
            }
        }
        if (validSpecs.isEmpty()) {
            LOGGER.error("[matheaufgabenmod] no valid section specs in config; using defaults");
            return new ModConfig(interval, ModConfig.DEFAULT.sectionSpecs());
        }
        return new ModConfig(interval, List.copyOf(validSpecs));
    }

    /** Direct deserialisation target — populated by Gson. */
    private static final class RawConfig {
        int intervalMinutes;
        List<String> sectionSpecs;
    }
}
```

- [ ] **Step 5: Run, confirm pass**

```sh
./gradlew test --tests ConfigLoaderTest
```

Expected: 7 tests passed.

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/config/ModConfig.java \
        src/main/java/dev/asante/matheaufgabenmod/config/ConfigLoader.java \
        src/test/java/dev/asante/matheaufgabenmod/config/ConfigLoaderTest.java
git commit -m "Add ModConfig and ConfigLoader with fallback-to-defaults policy"
```

---

## Task 10: PromptScheduler (TDD with injectable client surface)

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/timer/ClientSurface.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/timer/PromptScheduler.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/timer/PromptSchedulerTest.java`

The scheduler talks to Minecraft through a tiny `ClientSurface` interface that describes the bits we actually need (`hasWorld`, `isPaused`, `currentScreenIsPrompt`, `setPromptScreen`). This makes the scheduler testable in plain JUnit without a Minecraft runtime — the production wiring (Task 13) implements `ClientSurface` against the real `MinecraftClient`.

- [ ] **Step 1: Write `ClientSurface.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/timer/ClientSurface.java
package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;

/**
 * Narrow façade over MinecraftClient — only the bits the scheduler needs. Lets
 * us unit-test scheduling logic without booting Minecraft.
 */
public interface ClientSurface {
    boolean hasWorld();
    boolean isPaused();
    boolean currentScreenIsPrompt();
    void openPromptScreen(Problem problem);
}
```

- [ ] **Step 2: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/timer/PromptSchedulerTest.java
package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PromptSchedulerTest {

    /** Test double that records every prompt opened. */
    private static final class FakeClient implements ClientSurface {
        boolean hasWorld = true;
        boolean isPaused = false;
        boolean currentIsPrompt = false;
        final List<Problem> opened = new ArrayList<>();
        @Override public boolean hasWorld() { return hasWorld; }
        @Override public boolean isPaused() { return isPaused; }
        @Override public boolean currentScreenIsPrompt() { return currentIsPrompt; }
        @Override public void openPromptScreen(Problem p) { opened.add(p); currentIsPrompt = true; }
    }

    private static ModConfig oneMinute(String... specs) {
        return new ModConfig(1, List.of(specs));
    }

    private static void tickN(PromptScheduler sched, FakeClient client, int n) {
        for (int i = 0; i < n; i++) sched.onTick(client);
    }

    @Test
    void firesAfterIntervalElapsed() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        // 1 minute = 60 * 20 = 1200 ticks
        tickN(sched, client, 1200);
        assertEquals(1, client.opened.size());
    }

    @Test
    void doesNotFireBeforeInterval() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1199);
        assertEquals(0, client.opened.size());
    }

    @Test
    void doesNotAdvanceWhilePaused() {
        FakeClient client = new FakeClient();
        client.isPaused = true;
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 5000);
        assertEquals(0, client.opened.size());
    }

    @Test
    void resetsTimerWhenWorldUnloads() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 600);   // halfway
        client.hasWorld = false;
        sched.onTick(client);        // world gone — counter reset
        client.hasWorld = true;
        tickN(sched, client, 1199);  // not enough to fire from a fresh start
        assertEquals(0, client.opened.size());
    }

    @Test
    void doesNotDoubleOpenWhilePromptUp() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        client.currentIsPrompt = true;  // a prompt is already up
        tickN(sched, client, 5000);
        assertEquals(0, client.opened.size());
    }

    @Test
    void picksFromConfiguredSpecs() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1200);
        Problem p = client.opened.get(0);
        assertTrue(p.prompt().contains(" + "), "expected a plus problem, got " + p.prompt());
    }

    @Test
    void firesAgainAfterAnotherInterval() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1200);
        client.currentIsPrompt = false;  // user closed the prompt
        tickN(sched, client, 1200);
        assertEquals(2, client.opened.size());
    }
}
```

- [ ] **Step 3: Run, confirm fail**

```sh
./gradlew test --tests PromptSchedulerTest
```

Expected: compilation error (`PromptScheduler` doesn't exist yet).

- [ ] **Step 4: Implement `PromptScheduler.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/timer/PromptScheduler.java
package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.config.SectionSpec;
import dev.asante.matheaufgabenmod.generator.Generator;
import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.generator.Registry;

import java.util.Random;

public final class PromptScheduler {

    private static final int TICKS_PER_MINUTE = 60 * 20;

    private final ModConfig config;
    private final Random rng;
    private int elapsedTicks = 0;

    public PromptScheduler(ModConfig config, Random rng) {
        this.config = config;
        this.rng = rng;
    }

    public void onTick(ClientSurface client) {
        if (!client.hasWorld()) {
            elapsedTicks = 0;
            return;
        }
        if (client.isPaused()) return;
        if (client.currentScreenIsPrompt()) return;
        elapsedTicks++;
        int threshold = config.intervalMinutes() * TICKS_PER_MINUTE;
        if (elapsedTicks >= threshold) {
            elapsedTicks = 0;
            client.openPromptScreen(pickProblem());
        }
    }

    /** Public so {@link dev.asante.matheaufgabenmod.screen.PromptScreen} can request a fresh problem. */
    public Problem pickProblem() {
        String specStr = config.sectionSpecs().get(rng.nextInt(config.sectionSpecs().size()));
        SectionSpec spec = SectionSpec.parse(specStr);
        Generator gen = Registry.get(spec.type());
        Object params = gen.parseParams(spec.params());
        return gen.generate(rng, params).get(0);
    }
}
```

- [ ] **Step 5: Run, confirm pass**

```sh
./gradlew test --tests PromptSchedulerTest
```

Expected: 7 tests passed.

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/timer/ \
        src/test/java/dev/asante/matheaufgabenmod/timer/
git commit -m "Add PromptScheduler with ClientSurface façade for unit-testability"
```

---

## Task 11: PromptScreen

**Files:**
- Create: `src/main/java/dev/asante/matheaufgabenmod/screen/PromptScreen.java`
- Create: `src/test/java/dev/asante/matheaufgabenmod/screen/PromptScreenTest.java`

PromptScreen depends on Minecraft's `Screen`, `TextFieldWidget`, and `ButtonWidget`, which can't be fully unit-tested in plain JUnit without a Minecraft runtime. The unit test covers the *answer-checking logic* (extracted into a static helper) and the wrong-answer flow's surface contract; the rest is verified manually via `runClient` in Task 13.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/dev/asante/matheaufgabenmod/screen/PromptScreenTest.java
package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptScreenTest {

    @Test
    void exactAnswerAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "7"));
    }

    @Test
    void answerWithLeadingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "  7"));
    }

    @Test
    void answerWithTrailingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "7  "));
    }

    @Test
    void remainderAnswerAcceptedWithSpaces() {
        // Division problems answer "3 R 1" — kid typing "3 R 1" works as-is.
        assertTrue(PromptScreen.checkAnswer(new Problem("13 : 4", "3 R 1"), "3 R 1"));
    }

    @Test
    void remainderAnswerWithSurroundingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("13 : 4", "3 R 1"), " 3 R 1 "));
    }

    @Test
    void wrongAnswerRejected() {
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "8"));
    }

    @Test
    void emptyInputRejected() {
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), ""));
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "   "));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```sh
./gradlew test --tests PromptScreenTest
```

Expected: compilation error.

- [ ] **Step 3: Implement `PromptScreen.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/screen/PromptScreen.java
package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public final class PromptScreen extends Screen {

    private final Supplier<Problem> problemSupplier;
    private Problem currentProblem;
    private TextFieldWidget inputField;
    private Text feedback = Text.empty();

    public PromptScreen(Supplier<Problem> problemSupplier, Problem initialProblem) {
        super(Text.translatable("matheaufgabenmod.prompt.title"));
        this.problemSupplier = problemSupplier;
        this.currentProblem = initialProblem;
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
        // Enter (key 257) submits.
        if (keyCode == 257) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmit() {
        if (checkAnswer(currentProblem, inputField.getText())) {
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        currentProblem = problemSupplier.get();
        inputField.setText("");
        feedback = Text.translatable("matheaufgabenmod.prompt.wrong");
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

- [ ] **Step 4: Run, confirm pass**

```sh
./gradlew test --tests PromptScreenTest
```

Expected: 7 tests passed.

- [ ] **Step 5: Run full test suite to verify nothing regressed**

```sh
./gradlew test
```

Expected: 76 tests passed (8 SectionSpec + 12 plus + 12 minus + 10 einmaleins + 9 division + 4 registry + 7 ConfigLoader + 7 PromptScheduler + 7 PromptScreen).

- [ ] **Step 6: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/screen/PromptScreen.java \
        src/test/java/dev/asante/matheaufgabenmod/screen/PromptScreenTest.java
git commit -m "Add PromptScreen (Screen subclass + extracted checkAnswer helper)"
```

---

## Task 12: Wire everything in MatheaufgabenMod (entrypoint)

**Files:**
- Modify: `src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java`
- Create: `src/main/java/dev/asante/matheaufgabenmod/timer/MinecraftClientSurface.java`

The entrypoint loads the config, instantiates the scheduler, registers a `ClientTickEvents.END_CLIENT_TICK` listener that calls `onTick` with a real-Minecraft `ClientSurface`.

- [ ] **Step 1: Implement `MinecraftClientSurface.java`**

```java
// src/main/java/dev/asante/matheaufgabenmod/timer/MinecraftClientSurface.java
package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.screen.PromptScreen;
import net.minecraft.client.MinecraftClient;

import java.util.function.Supplier;

/** Production ClientSurface backed by a real MinecraftClient and the scheduler. */
public final class MinecraftClientSurface implements ClientSurface {

    private final MinecraftClient client;
    private final Supplier<Problem> problemSupplier;

    public MinecraftClientSurface(MinecraftClient client, Supplier<Problem> problemSupplier) {
        this.client = client;
        this.problemSupplier = problemSupplier;
    }

    @Override
    public boolean hasWorld() { return client.world != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public boolean currentScreenIsPrompt() { return client.currentScreen instanceof PromptScreen; }

    @Override
    public void openPromptScreen(Problem problem) {
        client.setScreen(new PromptScreen(problemSupplier, problem));
    }
}
```

- [ ] **Step 2: Replace `MatheaufgabenMod.java` with the full wiring**

```java
// src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java
package dev.asante.matheaufgabenmod;

import dev.asante.matheaufgabenmod.config.ConfigLoader;
import dev.asante.matheaufgabenmod.config.ModConfig;
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
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        ModConfig config = ConfigLoader.loadOrCreate(configPath);

        Random rng = new Random();
        PromptScheduler scheduler = new PromptScheduler(config, rng);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSurface surface = new MinecraftClientSurface(client, scheduler::pickProblem);
            scheduler.onTick(surface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} section spec(s)",
                MOD_ID, config.intervalMinutes(), config.sectionSpecs().size());
    }
}
```

- [ ] **Step 3: Run the full test suite**

```sh
./gradlew test
```

Expected: same count as Task 11's final step (the entrypoint isn't unit-tested here — it's smoke-tested via `runClient` in the next step).

- [ ] **Step 4: Manually smoke-test via runClient**

Run:
```sh
./gradlew runClient
```

In the dev Minecraft instance:
1. From the title screen, click "Singleplayer" → "Create New World" → leave defaults → "Create World".
2. The world should load. Confirm the launcher console shows `[matheaufgabenmod] initialised — interval=5 min, 4 section spec(s)`.
3. Quit Minecraft. Edit `<minecraft>/config/matheaufgabenmod.json` (the `<minecraft>` here is the project's `run/` directory created by Loom): set `"intervalMinutes": 1`.
4. Run `./gradlew runClient` again, load the same world.
5. Wait 60 seconds of in-world time (don't open the inventory/pause menu).
6. The PromptScreen should appear: title "Mathe-Aufgabe!", a centred prompt like "23 + 45 =", a text input, an "Antworten" button. The world should freeze.
7. Type the wrong answer; press Enter. The prompt should change to a new problem; the red "Falsch — versuch's nochmal" message should appear.
8. Type the correct answer; press Enter. The screen should close and the world should resume.
9. Press Esc — the kid attempting to bail out — verify the screen does NOT close.

If any step fails, inspect the launcher's console for stack traces and fix before committing.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java \
        src/main/java/dev/asante/matheaufgabenmod/timer/MinecraftClientSurface.java
git commit -m "Wire scheduler and screen via ModInitializer + MinecraftClientSurface"
```

---

## Task 13: README and CLAUDE.md

**Files:**
- Create: `README.md`
- Create: `CLAUDE.md`

- [ ] **Step 1: Write `README.md`**

```markdown
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
  the per-generator test classes for the supported parameters of each type, or read
  `Generator.describe()` output (printed at mod load if the config has invalid specs).

## Build

```sh
./gradlew build              # compile + test + assemble jar
./gradlew test               # JUnit only
./gradlew runClient          # launch a dev Minecraft instance with the mod loaded
```

Java 21 toolchain required (Loom auto-pulls if absent).

The design spec lives at `docs/superpowers/specs/2026-05-10-minecraft-matheaufgaben-mod-design.md`.
```

- [ ] **Step 2: Write `CLAUDE.md`**

```markdown
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
```

- [ ] **Step 3: Commit**

```sh
git add README.md CLAUDE.md
git commit -m "Add README and CLAUDE.md"
```

---

## Final verification

- [ ] Run the full test suite one last time:

```sh
./gradlew test
```

Expected: all tests green.

- [ ] Run `./gradlew build` — confirm the jar is assembled at `build/libs/matheaufgabenmod-0.1.0.jar`.

- [ ] Run `./gradlew runClient` and execute the manual smoke from Task 12 Step 4 again.

- [ ] `git log --oneline` should show ~13 atomic commits, each described by what it adds.
