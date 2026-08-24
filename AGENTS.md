# AGENTS.md

Offline-first personal finance tracker for Android. Single Gradle module (`:app`): Kotlin + Jetpack Compose + Room + Material 3. Namespace `com.example.hisab`. No CI, no custom lint config.

Deep per-file index with verified line refs: `docs/CODEBASE_INDEX.md` — consult it before touching SMS pipeline, backup/export, or icon rendering.

## Commands (Windows)

- Debug build: `.\gradlew.bat assembleDebug`
- Unit tests (plain JUnit4, no device needed): `.\gradlew.bat :app:testDebugUnitTest`
- Single test class: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.hisab.SmsBankParserTest"`
- Instrumented tests (requires running emulator/device): `.\gradlew.bat connectedDebugAndroidTest`
- Release APK: `.\gradlew.bat assembleRelease` (signed only if `release.keystore` exists at repo root)
- Built with JDK 21; Gradle config cache and parallel execution are enabled in `gradle.properties`.

If emulator install fails with `INSTALL_FAILED_INSUFFICIENT_STORAGE`, run the `cleanEmulatorTemp` task (clears `/data/local/tmp` via adb).

## Toolchain quirks

- AGP 9.x has built-in Kotlin support: do NOT apply `org.jetbrains.kotlin.android`. Only `android-application`, `kotlin-compose`, and `ksp` plugins are used (see root `build.gradle.kts`).
- Dependencies are pinned in the version catalog `gradle/libs.versions.toml`.
- `local.properties` contains a machine-specific SDK path; never edit/commit it.

## Architecture

- UI entry: `MainActivity` → `ui/navigation/HisabNavHost.kt`; screens + co-located ViewModels live in `ui/screens/<feature>/`.
- Data layer: Room entities/DAOs in `data/db/`, exposed via repositories in `data/repository/`. DB singleton: `HisabDatabase.getDatabase()`.
- SMS auto-log pipeline (`data/sms/`): `SmsReceiver` → `SmsBankParser.parse()` + `BankAliasRegistry` (maps short codes ↔ full bank names ↔ DLT sender headers, e.g. `BOB` ↔ `Bank of Baroda` ↔ `BOBTXN`) → dedup + balance reconciliation → `PendingTransactionEntity` + 2-stage actionable notification (`NotificationActionReceiver`).
- Keep `SmsBankParser` / `BankAliasRegistry` free of Android framework imports — they are tested as plain JVM code in `app/src/test/`.

## Room migrations (critical)

Schema version lives in `HisabDatabase.kt` (currently 7) with hand-written `MIGRATION_3_4 … MIGRATION_6_7` plus `fallbackToDestructiveMigration(true)`. Any entity change MUST add an explicit migration to `addMigrations(...)` — a missed migration silently wipes all user data on upgrade instead of failing the build.

## Conventions & gotchas

- Category icons are raw Unicode emoji strings in `categories.iconName`; render them via `SmsNotificationHelper.getCategoryEmoji()` (passes emojis through, maps only legacy Material names). Do NOT use `CategoryIconMapper.getIcon()` — it is dead code; only `getAccountIcon()` is alive.
- Every transaction/account/category mutation triggers a FULL JSON backup rewrite to ≤13 filesystem/MediaStore locations (`AutoBackupManager.performBackup()`); failures are swallowed by empty catches.
- CSV export/import use INCOMPATIBLE dialects: the app's exported 9-column CSV cannot be re-imported (importer expects legacy 6-column format). JSON export ignores month filters entirely.
- SMS dedup hashes live in SharedPreferences `"sms_processed_hashes"` and NEVER expire (a "7-day window" comment says otherwise).
- Launcher icons are generated externally into `easyappicon-icons-*/`; import them with `copy_icons.bat`.
- The manifest declares NO `INTERNET` permission — offline-first is enforced at the OS level. Any networked feature requires a manifest change and breaks the app's core privacy guarantee.
- Release flow: bump `versionCode`/`versionName` in `app/build.gradle.kts`, copy the signed APK into `releases/` (APKs ARE committed to git), update README download links/tag.
- Docs drift: README prose lags code (claims v3.1.2 while `versionName` is 3.2.0 and DB schema is 7). Trust code/config over README.
- `issues.txt` = user-reported bugs; `implementation_plan.md` and `walkthrough.md` document the planned/applied fixes for them.
