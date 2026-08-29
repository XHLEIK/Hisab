# Hisab — Extreme APK Size Optimization & Installation Reliability
## Implementation Plan — Approved with Corrections (2026-08-28)

> **Status:** Approved for Phase 0 execution. Incorporates 3 corrections from review (2026-08-28).
> **Objective:** Make Hisab as small and installable as technically possible while *zero* functional, visual, reliability, security, or financial-correctness regression. User experience indistinguishable.

---

### Corrections Applied (Blocking)

**1. `resConfigs "en"` — Not Approved as Written → Corrected:**
> **Only add `resConfigs` after measuring the packaged locale set and proving that restricting resources does not affect any supported localization, library behavior, accessibility, or runtime resource lookup. Do not assume `en` is safe solely because the app currently has no custom locale resources.**

Action: Before any `resConfigs` change, run `apkanalyzer resources packages` + `aapt2 dump resources --values` + `unzip -l | grep values-` to list all packaged locales from dependencies (e.g., `androidx`, `material`, `play-services`). Document which libraries contribute non-`en` values and whether they are referenced at runtime. Only restrict if proven safe.

**2. POI Replacement Stays Last — Binding:**
Dead code → icons → shrinking → R8 → **POI replacement last**. Do not let agent jump to custom writer. Requires 10k-row fixture opened in Excel/Sheets/LibreOffice with byte-identical header style, 10 cols, UTF-8, `autoSizeColumn` parity, verified before deleting `poi-ooxml`.

**3. Release-First Principle — Binding:**
> **Optimize release APK/AAB first; debug APK size is diagnostic only.** Debug deliberately remains unshrunk for development. `Debug: 93 MB / Release: 32 MB / AAB: 25 MB` is acceptable. Do not perform aggressive changes solely to shrink `app-debug.apk` if `release` is already well-optimized.

---

### Baseline Snapshot (from explore agents, 2026-08-27)

- **Build:** `:app` only, AGP 9.0.1, Kotlin 2.0.21, Gradle 9.1.0, JDK 21, `compileSdk 36`, `minSdk 28`, `targetSdk 36`, `versionCode 320`, `versionName 3.2.0`
- **Release config:** `isMinifyEnabled=false`, `isShrinkResources` absent, `resConfigs`/`abiFilters` absent, `proguard-rules.pro` empty template, `fallbackToDestructiveMigration(true)`
- **Largest contributors (apkanalyzer + graphify):** Debug APK ~93MB / 327k methods / ~86MB dex → `poi-ooxml 5.2.5` = 174k (53% dex), `material-icons-extended` = 23.5k (7%, 48 icons used but ~2000 packaged, `getIcon()` dead), `vico` = dead (0 imports, custom Canvas charts), `haze` = 2 usages, `gson` = 0 imports in `src/main` (uses `org.json`), `google-fonts` = GMS provider
- **Resources:** `res/` 28 files (15 PNGs ×5 densities, 2 xml wrappers, 2 drawables, 5 values, 2 xml). No `assets/`, `font/`, `drawable-*`. `easyappicon` duplicates every `mipmap` PNG byte-identical.
- **Graphify:** 922 nodes / 777 edges / 147 communities. God nodes: `TransactionProcessorTest` (51), `FakeTransactionDao` (27). Ponytail-audit expected: `vico`, `gson`, dead charts, `CategoryIconMapper.getIcon`, `SummaryCard`.

---

### Phase 0 — Baseline (BLOCKING, Do Before Any Deletion)

**Goal:** Record ground truth on current commit (`HEAD`).

**Commands (exact, per AGENTS.md):**
```bash
./gradlew :app:assembleDebug :app:assembleRelease --rerun-tasks  # release requires release.keystore at root
ls -lh app/build/outputs/apk/*/*.apk app/build/outputs/bundle/*/*.aab 2>/dev/null
apkanalyzer apk summary app/build/outputs/apk/debug/app-debug.apk
apkanalyzer dex packages --defined-only app/build/outputs/apk/debug/app-debug.apk | sort -k2 -nr | head -20
apkanalyzer dex packages --defined-only app/build/outputs/apk/release/app-release.apk | sort -k2 -nr | head -20
apkanalyzer resources packages app/build/outputs/apk/debug/app-debug.apk | head -40
unzip -l app/build/outputs/apk/debug/app-debug.apk | awk '{print $1, $4}' | sort -nr | head -60
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "\.so|values-|drawable-" | head -40
bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab --output=/tmp/app.apks --overwrite 2>/dev/null; unzip -l /tmp/app.apks | head -40
python -m graphify . --mode deep --no-viz  # or ponytail's graphify wrapper
ponytail-audit  # or: npx @dietrichgebert/ponytail audit
adb shell dumpsys diskstats | tr ',' '\n' | grep Data-Free
adb shell df -h /data
adb shell pm list packages -F | grep hisab
```

**Record in `docs/BASELINE.md`:**
- appId, versionName/Code, min/target/compileSdk
- `debug.apk` MB, `release.apk` MB, `release.aab` MB, installed size (`dumpsys package`), dex MB + methods (total + top 10 packages), resources MB, assets MB, native .so list (empty today), duplicate resources
- Graphify `graph.json` weight per community, Ponytail audit ranked list
- Emulator: `disk.dataPartition.size` in `~/.android/avd/<name>.avd/config.ini`, `Data-Free` bytes/%, `df -h /data` total/free

**Top Size Contributors (to be proven with numbers, not fabricated):**
1. `poi-ooxml` + transitives — expected ~45-50MB unshrunk
2. Unshrunk dex (327k methods) — no R8
3. `material-icons-extended` — 7% dex
4. `vico` (dead)
5. Unshrunk resources (no `resConfigs`/`shrinkResources`)
6. `haze`/`google-fonts` (<2MB each)

**Exit criteria:** `docs/BASELINE.md` committed with actual measurements; `graphify-out/GRAPH_REPORT.md` and `ponytail-audit` output saved; emulator storage proven >500MB free or AVD resized + wiped before install test. No code changes yet.

---

### Phase 1 — Classify Everything (A–G)

For each of 22 deps, fill:

| Dep | Class | Evidence | Decision |
|---|---|---|---|
| `poi-ooxml` | B (XLSX only) + F | `ReportGenerator.kt:438` only `XSSFWorkbook`, `gradle :app:dependencies` shows `xmlbeans` etc. | Keep behavior, evaluate modularize vs minimal writer (last) |
| `gson` | D | `grep gson=0` in `src/main` | Move to `testImplementation` |
| `vico` | D | `grep vico=0` in `src/main`, charts are Canvas | Delete |
| `material-icons-extended` | G | 48 used of ~2000 | Demote to `icons-core` + vectors |
| `haze` | B/G | 2 usages | Keep or RenderEffect |
| ... | ... | ... | ... |

**Rule:** Never remove because "it compiles". Prove runtime/resources/reflection/manifest usage via Graphify edges + `grep` + `apkanalyzer`.

---

### Phase 2 — Why ~100MB (Prove, Don't Assume)

- **POI:** Transitive tree + `apkanalyzer dex | grep poi`, check `poi-ooxml-lite` alternative, test `exclude group:commons-compress` impact, prototype minimal writer behind flag and prove Excel/Sheets/LibreOffice parity on 10k rows.
- **PDF:** Verify no hidden PDF lib (`unzip -l | grep font|pdf`); check duplicate resources.
- **Compose:** BOM manages versions, but `vico` duplicates `compose`/`compose-m3`.
- **Icons:** Prove 48 icons subset.
- **Resources:** List packaged locales via `aapt2`, prove `en` restriction safe per Correction 1.
- **Native:** `unzip -l | grep .so` — expect none; add `abiFilters` guard.
- **Debug:** `apkanalyzer files` — ensure `graphify-out`, `releases/*.apk`, `easyappicon/ios` not packaged.

---

### Phase 3 — Build Optimization (R8) — Highest Leverage, Highest Risk

**Current:** `isMinifyEnabled false` despite `proguard-android-optimize.txt` reference.

**Steps (release only):**
1. `isMinifyEnabled = true`, `isShrinkResources = true` (keep `debug` unshrunk)
2. Start with `proguard-android-optimize.txt` + empty `proguard-rules.pro`, then add minimal keeps only on `Missing class` crash:
   - Room: `-keep class com.example.hisab.data.db.** { *; }` + `-keep @androidx.room.Entity class *`
   - `TransactionType`, `SmsReceiver` (`BROADCAST_SMS`), `NotificationActionReceiver`
   - `AutoBackupManager` JSON via `org.json` — no Gson keep needed after move
3. `assembleRelease` + `adb install -r` + full regression (Phase 9-12) after each keep. Do not weaken keeps for size.

---

### Phase 4 — Replace Heavy Libraries Only With Proven Parity

Decision matrix per candidate: correctness, API parity, runtime stability (API 28), Android compat, export compat, visual parity, performance, maintenance, transitive size.

**Order is binding:** Do not jump to POI before dead code/icons/shrinking/R8.

---

### Phase 5 — Visual Quality Protected (No Negotiation)

Keep: `duration/easing/transitions`, `blur 16dp/alpha 0.88`, shadows, typography (`Outfit`/`Inter`), chart types, bottom bar `HazeState`, icons, emoji picker. Verify after each R8 build: Dashboard hero, CategoryPicker (40 icons), EmojiPickerSheet, History, Analytics (Donut+UnifiedBar), Settings (4 accordions), notifications 2-stage.

---

### Phase 6 — Split/Modularize Only If Zero UX Change

Investigate lazy init (`by lazy` already used) but **do not** introduce `dynamicFeature`/`splitInstall` — single-module app, offline-first, no Play Feature Delivery.

---

### Phase 7 — APK vs Install Footprint (Separate Problems)

Per `AGENTS.md:10-25`, `INSTALL_FAILED_INSUFFICIENT_STORAGE` gate is `min(10% partition, 500MB)` = 500MB on 5.8GB AVD, plus 2-3× dex for `oat`. Test on **clean** AVD with `disk.dataPartition.size` enlarged to 8GB + `emulator -wipe-data`, or prove `Data-Free` >500MB before blaming APK. Measure installed size via `dumpsys package` and required install space separately.

---

### Phase 8 — Baseline / Optimization / Comparison After Each Batch

After each of P1–P4, append to `docs/OPTIMIZATION_BASELINE.md`:
```
Batch N — <what changed>:
  debug.apk: 93.0 -> 41.2 (-51.8)
  release.apk: ? -> 32.1
  aab: ? -> 25.4
  dex: 86MB/327k -> 34MB/142k
  resources: ? -> ?
```

Use `apkanalyzer` + `bundletool` + `graph.json` weight per community. Never report % without absolute numbers.

---

### Phase 9-12 — Regression Gates (Every R8 Build Must Pass All 4)

- **Functional:** 90 JVM tests + `SmsBankParserTest` (BOB 40/45/30), `TransactionProcessorTest` (duplicate, reference identity, catch-up vs realtime race, `recoverUnnotified` 48h/5), split `gross - reimbursed = net`, migration `7→8` & `8→9` (`sqlite-jdbc`), `ReportGenerator` 10k rows open in Excel/Sheets, `AutoBackupManager` restore
- **Visual:** Screenshot Dashboard, Add Category, EmojiPicker, History, Analytics, Settings, notifications Stage1/2, glass dock
- **Performance:** Cold start, dashboard `getAllTransactionsFlow`, emoji grid fling, history 1k rows, XLSX 10k rows — `adb shell am start -W` — no >10% regression
- **Security:** Permissions unchanged (no `INTERNET`), `BankAliasRegistry.matches` gate, `UNIQUE(sourceMessageHash)` enforced, `release.keystore` signs, no `MANAGE_EXTERNAL_STORAGE`

---

### Phase 13 — Release Build

`./gradlew :app:assembleRelease` (requires `release.keystore`) and `bundleRelease` if configured. Compare `debug.apk` (unshrunk) vs `release.apk` (minified) vs `aab`. Per Correction 3, **release/AAB are primary**; debug size is diagnostic.

---

### Implementation Order (Branch Per Batch, `build` + `test` After Each)

1. **Batch A — Deletions (safest, ~8-10MB):** `vico` + `gson(main)` + dead charts (`CategoryTrendChart` etc.) + dead `CategoryIconMapper.getIcon` → PR + `testDebugUnitTest` + screenshots
2. **Batch B — Icons (7% dex):** `icons-core` migration → PR + `dex` + Settings screenshots
3. **Batch C — Config (resource shrinking):** *Corrected* `resConfigs` only after locale proof + `shrinkResources` + `isMinifyEnabled` with empty rules → PR with full regression matrix
4. **Batch D — R8 tightening** → PR with `release.apk` install test on clean AVD
5. **Batch E — POI** → feature-flag minimal writer, parity suite, then cutover — **largest, last, most reviewed**
6. **Batch F — Packaging/ABI + final report** → `docs/OPTIMIZATION_REPORT.md` (9 sections per `optimize.md`)

---

### Hard Rules & Ponytail Ladder

- **Priority 1 (YAGNI/delete):** Delete dead code — shortest diff wins
- **Priority 2 (duplicate):** Icons, easyappicon ldpi, mipmap wrappers
- **Priority 3 (config):** Resource shrinking (proven safe)
- **Priority 4 (R8):** Minify with minimal keeps
- **Priority 5 (ABI):** Guard `abiFilters`
- **Priority 6 (replace):** POI only with parity proof
- **Priority 7 (arch):** No dynamic features

**Ponytail:** *The best code is the code never written; deletion over addition; shortest working diff.* Ladder: Does it need to exist? → Already in codebase? → Stdlib? → Native platform? (e.g., `RenderEffect` vs `haze`) → One-liner? Only then minimal code.

**Graphify:** `graph.json` + `GRAPH_REPORT.md` for dependency graph, transitive duplication, largest packages, reachable vs unreachable, dispatch semantic subagents for cross-file relationships, verify no visual regression via community cohesion.

---

### Required Final Report (9 Sections) & Definition of Done

Per `optimize.md` Required Final Report (root cause, before/after APK/AAB/DEX/resources/native/largest deps, removed, replaced, intentionally not changed, regression results, build results, installation test, visual verification, final recommendation) and **10-item DoD** — all must be true before task complete.

**Next Step on Approval:** Execute **Phase 0 baseline** (`assembleDebug` + `assembleRelease` if keystore present + `apkanalyzer` + `graphify --mode deep` + `ponytail-audit`) and write `docs/BASELINE.md` before any deletion.
