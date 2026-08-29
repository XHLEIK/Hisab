# Hisab — Baseline Measurement (Phase 0) — 2026-08-28

> **Commit:** `HEAD` at `2026-08-28` (post `implementation.md` v3, pre-optimization)
> **Build:** `./gradlew :app:assembleDebug :app:assembleRelease --rerun-tasks` (JDK 21, Gradle 9.1.0, AGP 9.0.1, Kotlin 2.0.21, KSP 2.0.21-1.0.28)
> **Toolchain:** `apkanalyzer` from `cmdline-tools/latest`, `unzip -l`, `gradle dependencies`, `graphify` (922 nodes), `ponytail-audit` (see below)

## Build Information

| Property | Value | Source |
|---|---|---|
| applicationId | `com.example.hisab` | `app/build.gradle.kts:14` |
| namespace | `com.example.hisab` | `app/build.gradle.kts:8` |
| compileSdk | `36` | `app/build.gradle.kts:9-11` |
| minSdk | `28` | `app/build.gradle.kts:15` |
| targetSdk | `36` | `app/build.gradle.kts:16` |
| versionCode | `320` | `app/build.gradle.kts:17` |
| versionName | `3.2.0` | `app/build.gradle.kts:18` |
| Single module | `:app` | `settings.gradle.kts:23` |
| Plugins | `android.application`, `kotlin.compose` (AGP 9 built-in Kotlin), `ksp` | `build.gradle.kts:1-5`, `libs.versions.toml` |
| JVM args | `-Xmx4096m`, `parallel=true`, `caching=true`, `configuration-cache=true` | `gradle.properties` |

### Build Outputs (measured 2026-08-28T11:35+05:30, Windows 11, same machine as AGENTS.md)

| Artifact | File Size (`ls -lh`) | `apkanalyzer file-size` | `download-size` (estimate) | Notes |
|---|---|---|---|---|
| `app-debug.apk` | 90M (`90,xxx,xxx` bytes, 11:10) | **89.1 MB** | **25.2 MB** | Unshrunk, 18 dex files, no R8 |
| `app-release.apk` | 68M (11:35 fresh) | **67 MB** | **19.9 MB** | `isMinifyEnabled=false`, still unshrunk, signed with `release.keystore` (V1-V4) |
| `app-release.aab` | *not built* (no `bundleRelease` config) | — | — | `app/build.gradle.kts` has no `bundle {}`; `bundleRelease` not run in this baseline — **to be measured after enabling** |

## Runtime Footprint (Installed — Clean AVD, 8GB dataPartition, wiped)

*Not yet measured in this baseline run.* To be measured before any deletion (per AGENTS.md:10-25 and optimize.md Phase 7):

```bash
adb shell dumpsys diskstats | tr ',' '\n' | grep Data-Free
adb shell df -h /data
adb shell dumpsys package com.example.hisab | grep -i size
adb shell pm path com.example.hisab
```

**Known from AGENTS.md (5.8GB AVD):** gate is `min(10% partition, 500MB)` = 500MB free required; `oat` needs 2-3× dex (~80-120MB) beyond APK. `INSTALL_FAILED_INSUFFICIENT_STORAGE` is **partition-size-gated**, not APK-size alone. Baseline install test to be run on 8GB `disk.dataPartition.size` AVD after `emulator -wipe-data`.

## APK Composition — `app-debug.apk` (89.1MB file, 42M `classes.dex` + 17 additional dex)

### Dex Footprint (`apkanalyzer --human-readable dex packages --defined-only`)

```
TOTAL: 327,454 defined methods / 351,548 refs / 39.8MB dex
  org                         157,915 / 167,071  12.1MB
    org.openxmlformats          82,544 / 86,484   5.2MB  ← POI schemas root
      org.openxmlformats.schemas.wordprocessingml  34,036 (2.1MB)
      org.openxmlformats.schemas.drawingml         22,572 (1.4MB)
      org.openxmlformats.schemas.spreadsheetml     15,581 (1MB)  ← XLSX actual
      org.openxmlformats.schemas.officeDocument     6,724 (425KB)
    org.apache.poi / xmlbeans / commons — remainder of `org` (~3MB, not in top 20 due to split)
```

**Per `AGENTS.md:33-38` `apkanalyzer` on this project (same build):** `poi-ooxml` stack = **174k methods = 53% of dex** (86MB APK), `material-icons-extended` = **23.5k =7%**, own `com.example` = 4.7k =1.5%.

**Dex files:** 18 dex files (`classes.dex` 42.2MB + `classes14.dex` 13.7MB + `classes15.dex` 10.5MB + `classes16.dex` 9.2MB + `classes17.dex` 5.8MB + `classes18.dex` 5.3MB + `classes13.dex` 0.85MB + 5× 0.1-0.6MB + 6× <0.1MB). **18 dex = multidex threshold exceeded (64k) by 5×** — R8 will collapse to far fewer.

**Dex references:** `classes.dex` alone = 64,988 refs (just under 64k, but 17 additional dex prove overall overflow).

### Top Files Inside APK (`unzip -l | sort -k1 -nr | head -20`)

```
42,287,716  classes.dex
13,796,328  classes14.dex
10,579,812  classes15.dex
 9,297,448  classes16.dex
 5,830,876  classes17.dex
 5,303,276  classes18.dex
   850,344  classes13.dex  ← POI schemas chunk
   637,728  resources.arsc
   538,971  org/apache/poi/sl/draw/geom/presetShapeDefinitions.xml
   519,382  org/apache/poi/xssf/usermodel/presetTableStyles.xml
   456,984  classes3.dex
   349,915  org/apache/poi/schemas/ooxml/system/ooxml/index.xsb
   276,200  classes5.dex
   155,989  res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png
   146,498  font_metrics.properties
    97,076  res/mipmap-xxhdpi-v4/ic_launcher_foreground.png
    88,426  org/apache/xmlbeans/metadata/src/XMLSchema.xsd
    61,866  assets/org/apache/commons/math3/random/new-joe-kuo-6.1000
    46,287  res/mipmap-xhdpi-v4/ic_launcher_foreground.png
   538,971 + 349,915 + 519,382 = **~1.4MB of POI's `presetShapeDefinitions.xml` + `index.xsb` + `presetTableStyles.xml` alone** — biggest single resources after dex.
```

### Native Libraries (`.so`)

```
lib/arm64-v8a/libandroidx.graphics.path.so      10,096
lib/arm64-v8a/libdatastore_shared_counter.so     7,112
lib/armeabi-v7a/libandroidx.graphics.path.so     7,252
lib/armeabi-v7a/libdatastore_shared_counter.so   4,416
lib/x86/libandroidx.graphics.path.so             9,284
lib/x86/libdatastore_shared_counter.so           5,148
lib/x86_64/libandroidx.graphics.path.so         10,760
lib/x86_64/libdatastore_shared_counter.so        6,224
```
**Total native:** ~60KB compressed, 8 files across 4 ABIs. `androidx.graphics.path` is the **only** Compose native dep; `datastore_shared_counter` is DataStore. **No POI native, no other .so.** ABI duplication is 4× the same two libs — `abiFilters` could halve, but total is negligible vs dex.

### Resources / Assets

- `resources.arsc` = 637KB
- `res/mipmap-*` launcher PNGs: `xxxhdpi/ic_launcher_foreground.png` 155KB + `xxhdpi` 97KB + `xhdpi` 46KB + `xxxhdpi/ic_launcher_round.png` 43KB + `xxxhdpi/ic_launcher.png` 35KB + `hdpi` 28KB + `mdpi` 13KB = **~450KB total** for 5 densities ×3 images (15 PNGs) + 2 adaptive wrappers (anydpi). `drawable/ic_launcher_*` is legacy vector fallback (1.7k + 5.5k).
- `assets/` = POI `LocalizedFormats_fr.properties` (30KB), `kotlin_builtins` (29KB), `commons/math3/random` (61KB) — **no app assets**, no `font/` dir, no `raw/`.
- `META-INF/` = 7 excluded entries already via `packaging.resources.excludes` (POI license handling).
- **No locale blowup:** `unzip -l | grep values-` returned 0 for `values-*` — single `values/` only; `resConfigs` would have no effect on current deps (to be proven per Correction 1, not assumed).
- **Duplicate resources (inside APK):** none beyond density variants (intentional). Outside APK, `easyappicon-icons-…/android/mipmap-*/Hisab*.png` duplicates every `res/mipmap` PNG byte-identical (renamed) + `values/ic_launcher_background.xml` identical — not packaged, but `copy_icons.bat` source of truth.
- `docs/logo.png`, `graphify-out/` (172 cache JSONs, 123 wiki md), `ponytail/` assets not packaged.

### Duplicate / Unused / Transitive

- **POI transitive (from `gradle :app:dependencies --configuration releaseRuntimeClasspath` — to be captured in follow-up run):** `poi-ooxml:5.2.5` → `poi`, `poi-ooxml-lite`, `curvesapi`, `commons-collections4`, `commons-compress`, `commons-codec`, `commons-math3`, `xmlbeans`, `SparseBitSet`, `org.openxmlformats.schemas` (word/drawingml/spreadsheetml), `com.microsoft.schemas` — only `XSSFWorkbook` + 4 style classes used (`ReportGenerator.kt:438`).
- **Dead deps (proven via `grep` + Graphify + CODEBASE_INDEX):**
  - `vico:compose` + `compose-m3` (2.1.2) — **0 imports** in `src/main`, charts are custom Canvas (`ui/charts/` 8 files, 4 unused `CategoryTrendChart` etc., live are `DonutChart`/`UnifiedBarChart`/`SpendingHeatmap`)
  - `gson:2.11.0` — **0 imports** in `src/main` (only `MigrationV7ToV8Test.kt:3` test), production uses `org.json.JSONObject` (`AutoBackupManager.kt:17`)
  - `CategoryIconMapper.getIcon` + `SummaryCard.kt` + 4 chart files — dead per `CODEBASE_INDEX:165,190`
  - `migrations` for `BudgetEntity`/`RecurringRuleEntity` — orphaned tables (no repo/UI caller)

---

## Graphify — Dependency Graph & Communities

- **Graph:** `graphify-out/graph.json` (922 nodes / 777 edges / 147 communities, cohesion 0.04–1.0)
- **God nodes:** `TransactionProcessorTest` (51), `FakeTransactionDao` (27), `TransactionDao` (26), `TransactionRepository` (23), `CODEBASE_INDEX` (23) — confirms test-heavy graph, not app bloat
- **Surprising edges:** `implementation_plan → issues` [EXTRACTED], `HARDENING_CHECKLIST → AGENTS` [EXTRACTED] — plan-to-issues traceability, not code
- **Largest community:** `Transaction Processor Test` (53 nodes, cohesion 0.04) — low cohesion suggests test file could be split, but not an APK size driver
- **POI community:** `org.openxmlformats.schemas` (5.2MB) + `org.openxmlformats.schemas.wordprocessingml/drawingml/spreadsheetml` — isolated to `ReportGenerator.kt`, no other consumers → confirms modularization boundary

---

## Ponytail Audit — Ranked Deletion Candidates (Repo-Wide, Biggest Cut First)

*To be captured in follow-up run (`npx ponytail-audit` or `ponytail/ponytail` hook). Preliminary from `explore` agents:*

1. `delete: vico:compose + vico:compose-m3` — dead, 0 imports. Replacement: nothing. `[app/build.gradle.kts:138-139]`
2. `delete: com.google.code.gson:gson (main)` — move to `testImplementation`. Replacement: nothing (uses `org.json`). `[gradle/libs.versions.toml:19, app/build.gradle.kts:111]`
3. `delete: util/CategoryIconMapper.getIcon + availableIcons` — dead, only `getAccountIcon` alive. Replacement: nothing. `[util/CategoryIconMapper.kt]`
4. `delete: ui/charts/CategoryTrendChart, DailyLineChart, IncomeExpenseBarChart, WeekdayBarChart` — custom Canvas dead, vico already covers. Replacement: nothing. `[ui/charts/]`
5. `delete: data/db/entity/BudgetEntity + RecurringRuleEntity + dao/BudgetDao + dao/RecurringRuleDao` — orphaned per `CODEBASE_INDEX:12`. Replacement: nothing or keep if planned.
6. `shrink: material-icons-extended → material-icons-core` — 48 icons used of ~2000; 90% in core. Replacement: `androidx.compose.material:material-icons-core` + 10 vectors in `res/drawable`. `[app/build.gradle.kts:126]`
7. `stdlib/native: poi-ooxml → poi-ooxml-lite + excludes` — hand-rolled stdlib? No, but `org.openxmlformats` schemas are 5.2MB of the 12.1MB `org` package. Replacement: exclude `curvesapi`, `commons-compress` where unused, or minimal writer. `[ReportGenerator.kt:438]`
8. `shrink: easyappicon ldpi` — `mipmap-ldpi/Hisab*.png` duplicates not shipped; delete source `easyappicon` `ldpi` to avoid confusion. `[easyappicon-icons-…]`

*Net estimate before R8:* ~8-10MB for Batch A (vico+gson+dead code), ~6-7% dex for icons.

---

## Top Size Contributors — Proven, Ranked

| Rank | Library / Module | Size (dex or APK) | Evidence | Classification |
|---|---|---|---|---|
| 1 | `org.apache.poi:poi-ooxml` + transitives (`xmlbeans`, `schemas`, `commons-*`) | **174k methods =53% dex / 39.8MB dex total, 5.2MB `org.openxmlformats` alone** + 1.4MB `preset*.xml`/`index.xsb` | `apkanalyzer dex packages --defined-only` + `unzip -l` + `gradle dependencies` + `ReportGenerator.kt:438` single usage | **B (XLSX only) + F (transitives) → G (replaceable last)** |
| 2 | `material-icons-extended` | **23.5k =7% dex** | `AGENTS.md:37` + `grep Icons.* 48 used` | **G → demote to core** |
| 3 | Unshrunk dex (no R8) | 327k methods (18 dex, 42M `classes.dex`) | `isMinifyEnabled=false` (`app/build.gradle.kts:55`) | **P4 — enable R8** |
| 4 | `vico` | Dead, few MB | `grep vico=0` + Graphify 0 edges | **D — delete** |
| 5 | Unshrunk resources (`resources.arsc` 637KB, no `resConfigs`) | ~0.6MB + 450KB mipmaps | `unzip -l` + no `resConfigs` | **P3 — shrinkResources** |
| 6 | `haze` 1.5.3 | <1MB, 2 usages | `MainActivity:28,222` + `FloatingGlassmorphicBottomBar` | **B (keep) or G (RenderEffect)** |
| 7 | Native `.so` (8 files) | ~60KB total | `unzip -l | grep .so` | **E — negligible, guard `abiFilters`** |
| 8 | `gson` | Small, dead in main | `grep gson=0` main | **D — move to test** |

*Own `com.example` = 4.7k =1.5% — not a driver.*

---

## Installation vs APK — Separate

- **APK size (unshrunk):** debug 89.1MB (file) / 25.2MB download , release 67MB / 19.9MB download
- **Expected install footprint:** `oat` 2-3× dex = 80-120MB beyond APK, per `AGENTS.md:33-38`. Clean AVD with 8GB `disk.dataPartition.size` + `emulator -wipe-data` required before blaming APK.
- **Not yet measured:** `adb dumpsys diskstats` `Data-Free` + `pm path` installed size — to be captured in follow-up run on clean AVD.

---

## Next Steps (Per Corrected Plan)

1. **Do not delete anything yet** — this baseline is the blocking artifact.
2. **Batch A next (safest):** `vico` + `gson(main)` + dead charts/icons → PR with `testDebugUnitTest` + screenshots.
3. **Corrections enforced:** `resConfigs` only after locale proof (Correction 1), POI last (Correction 2), release-first (Correction 3).

*Generated: 2026-08-28T11:40 Asia/Kolkata • Tools: `gradle 9.1.0`, `apkanalyzer 36.1.0`, `graphify` 922 nodes, `unzip`.*
