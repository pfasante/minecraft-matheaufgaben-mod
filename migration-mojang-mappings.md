# Mod-Update: Minecraft 1.21.11 → 26.1.2 (Fabric)

Strukturierte Zusammenfassung der Anpassungen für den Port des Matheaufgaben-Mods.

## TL;DR — Was sich grundlegend ändert

Mit Minecraft 26.1 hat Mojang den Versionssprung zum neuen Kalenderschema (26.x für 2026) **und** die vollständige Entfernung der Obfuskation in einem Schritt vollzogen. Das bedeutet:

- **Keine Mappings mehr** — Yarn ist eingestellt, "Mojang Mappings" werden nicht mehr separat ausgeliefert. Die Minecraft-JAR enthält die offiziellen Namen direkt.
- **Loom remapt nichts mehr** — neues Build-Plugin, neue Dependency-Konfigurationen.
- **Java 25** ist Pflicht (vorher 21).
- **Fabric API hat umbenannte Klassen** — viele API-Namen wurden an die offiziellen Mojang-Namen angeglichen.

---

## 1. Toolchain-Update

| Komponente | Vorher (1.21.11) | Nachher (26.1.2) |
|---|---|---|
| Java | 21 | **25** |
| Gradle | 8.x | **9.4.0** |
| Loom Plugin | `fabric-loom` (mit Remap) | `net.fabricmc.fabric-loom` 1.15 (ohne Remap) |
| Fabric Loader | 0.16.x | 0.18.6+ |
| Fabric API | `0.141.4+1.21.11` | **`0.149.0+26.1.2`** |
| Mappings | Yarn oder `officialMojangMappings()` | — entfällt komplett — |

**Java 25 lokal**: Auf Fedora via `sudo dnf install java-25-openjdk-devel` und `JAVA_HOME` setzen (für Gradle entscheidend, sonst Fehler `release version 25 not supported`).

---

## 2. `build.gradle` — konkrete Änderungen

### Plugin-Block

```diff
plugins {
-    id 'fabric-loom' version '1.7-SNAPSHOT'
+    id 'net.fabricmc.fabric-loom' version '1.15'
    id 'maven-publish'
}
```

### Java-Toolchain

```diff
java {
-    sourceCompatibility = JavaVersion.VERSION_21
-    targetCompatibility = JavaVersion.VERSION_21
+    sourceCompatibility = JavaVersion.VERSION_25
+    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
-        languageVersion = JavaLanguageVersion.of(21)
+        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

### Dependencies — der größte Umbau

```diff
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
-    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
-    // alternativ: mappings loom.officialMojangMappings()
+    // KEINE mappings-Zeile mehr
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
-    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
+    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
```

### Tasks

```diff
- remapJar { ... }
+ jar { ... }
- remapSourcesJar { ... }
+ sourcesJar { ... }
```

### `gradle.properties`

```diff
- minecraft_version=1.21.11
- yarn_mappings=1.21.11+build.1
- loader_version=0.16.10
- fabric_version=0.141.4+1.21.11
+ minecraft_version=26.1.2
+ loader_version=0.18.6
+ fabric_version=0.149.0+26.1.2
```

---

## 3. Code-Migration

### Yarn → offizielle Namen

Falls der Mod bisher Yarn-Mappings nutzt, müssen sämtliche Class/Field/Method-Referenzen umgeschrieben werden. Beispiele typischer Umbenennungen:

| Yarn | Offiziell (Mojang) |
|---|---|
| `Identifier` | `ResourceLocation` |
| `World` | `Level` |
| `PlayerEntity` | `Player` |
| `ItemStack#getItem()` | bleibt | 
| `Text` | `Component` |
| `MinecraftClient` | `Minecraft` |
| `ClientPlayerEntity` | `LocalPlayer` |

Die Fabric Docs liefern eine **IntelliJ-IDEA-Migration-Map** (XML-Datei), die den Großteil dieser Refactorings per "Migrate Code…" automatisch erledigt. Manuelle Nacharbeit ist v.a. bei Mixins nötig.

### Fabric-API-Umbenennungen

Auch APIs des Fabric-Projekts wurden angeglichen. Für den Matheaufgaben-Mod relevant sind vermutlich:

| Vorher | Nachher |
|---|---|
| `ItemGroupEvents` | `CreativeModeTabEvents` |
| `BlockRenderLayerMap.INSTANCE.putBlock(...)` | Wegfall — Layer kommt jetzt automatisch über Sprite-Properties, sonst über Block-Model oder Model-Loading-API |
| Diverse `*Registry`-Helper | An offizielle `BuiltInRegistries`-Namen angeglichen |

### Recipe-Serializer (falls verwendet)

Vereinfacht: keine innere `RecipeSerializer`-Klasse mehr, stattdessen direkt `MapCodec` + `StreamCodec`:

```java
Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, "math_recipe",
    new RecipeSerializer<>(MathRecipe.CODEC, MathRecipe.STREAM_CODEC));
```

### Mixins

- Mixin-Targets müssen auf die offiziellen Namen umgestellt werden (`Lnet/minecraft/world/level/Level;` statt `Lnet/minecraft/world/World;`).
- AccessWideners sind weiterhin nutzbar — Loom kann auch in der neuen Version Mixins remappen, falls altes Setup übergangsweise weiterläuft.
- `mcsrc.dev` ist nützlich, um die korrekten offiziellen Namen für Mixin-Targets nachzuschlagen.

---

## 4. Welt-/Ressourcen-Migration (falls Server-relevant)

Mojang hat in 26.1 die Welt-Verzeichnisstruktur reorganisiert:

- Overworld: `world/` → `world/dimensions/minecraft/overworld/`
- Nether: `world_nether/` bzw. `world/DIM-1/` → `world/dimensions/minecraft/the_nether/`
- End: `world_the_end/` bzw. `world/DIM1/` → `world/dimensions/minecraft/the_end/`

Konvertierung erfolgt beim ersten Serverstart automatisch. Eigene Scripts (Backups o.ä.), die direkt auf die Pfade zugreifen, müssen angepasst werden — für einen reinen Client-Mod aber irrelevant.

---

## 5. Empfohlene Reihenfolge

1. **Branch anlegen** (`port/26.1.2`) und vorab vollen Build auf 1.21.11 sichern.
2. **Java 25 installieren**, `JAVA_HOME` setzen, mit `java -version` und `gradle -v` verifizieren.
3. **Falls noch Yarn**: Im 1.21.11-Branch zunächst auf `officialMojangMappings()` migrieren und sicherstellen, dass alles baut und testet. Das entkoppelt die Mapping-Migration vom Versions-Upgrade.
4. **`build.gradle` umstellen** (Plugin, Java, Dependencies, Tasks) gemäß Abschnitt 2.
5. **`gradle.properties`** auf 26.1.2 / 0.149.0+26.1.2 setzen.
6. **`gradle build`** ausführen, IntelliJ neu importieren.
7. **Code-Migration**: IntelliJ-Migration-Map aus den Fabric Docs anwenden, dann Compile-Errors manuell durchgehen.
8. **Fabric-API-Umbenennungen**: `CreativeModeTabEvents` etc. (siehe oben).
9. **Mixins** prüfen und neue Target-Namen einsetzen.
10. **In-Game-Test** in einer separaten 26.1.2-Welt (nicht produktiv!).
11. **Modrinth-/CurseForge-Release** mit klarer Versions-Kompatibilität (`26.1.2` als `game_version`).

---

## 6. Nützliche Ressourcen

- **Fabric Migration Guide 26.1**: https://fabricmc.net/2026/03/14/261.html
- **mcsrc.dev** — dekompilierter Minecraft-Quellcode mit offiziellen Namen, Mixin-/AccessWidener-Helfer
- **Fabric API Releases**: https://github.com/FabricMC/fabric-api/releases
- **Fabric Docs Mappings**: https://wiki.fabricmc.net/tutorial:mappings

---

## 7. Geschätzter Aufwand

Für einen vergleichsweise kompakten Mod wie den Matheaufgaben-Mod (vermutlich primär UI/Screen-Klassen, Item- oder Chat-Integration, wenige bis keine Mixins in Renderer-Tiefen) realistisch:

- Toolchain + Buildscript: **1–2 h**
- IntelliJ-Migration + Compile-Fixes: **2–4 h**
- Fabric-API-Umbenennungen (`CreativeModeTabEvents` etc.): **1 h**
- Test + Polish: **2 h**

**Gesamt: ein knapper Arbeitstag**, sofern keine versteckten Mixins in Renderpipelines oder GL-Calls auftauchen. Falls der Mod rohe OpenGL-Aufrufe macht (eher unwahrscheinlich für Matheaufgaben), kommt Migration auf Blaze3D dazu — dann eher 2 Tage.
