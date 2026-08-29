# Hisab Codebase Index (verified)

Every fact below was verified against source (current as of the v3.2.1 SMS hardening). Line numbers refer to current files and may drift after edits; identifiers are verbatim. Companion quick-guide: root `AGENTS.md`. On-device verification the JVM suite cannot cover: root `HARDENING_CHECKLIST.md`.

## 1. Entry flow & wiring (no DI)

`HisabApplication.kt` — lazy singleton `database` (`HisabDatabase.getDatabase(this)`).

`MainActivity.kt` `onCreate` (276L): manually constructs `AutoBackupManager(applicationContext, database)` then `TransactionRepository(transactionDao, autoBackupManager)`, `CategoryRepository`, `AccountRepository(accountDao, transactionDao, autoBackupManager)`, `BackupRepository(...)`, `PendingTransactionRepository(pendingTransactionDao, transactionDao, database, autoBackupManager)`. All passed down through `setContent { HisabApp(...) } → HisabNavHost(...)`.

Startup side effects in MainActivity:
- `LaunchedEffect(Unit)` (~L127): guarded by a **companion `private var smsStartupWorkStarted`** — `LaunchedEffect(Unit)` re-runs after a configuration change on the same Activity instance, and re-running the SMS startup work would re-scan the whole catch-up window. Inside the guard, **`recoverUnnotified()` runs first, then `SmsCatchUpSync.runSync()`**. That order is deliberate: recovery must not be blocked by (or require) `READ_SMS`, so it can never be gated behind the catch-up scanner's permission check.
- Restore path: **unconditional, no permission and no prompt.** `database.ensureDefaults()` + `autoBackupManager.restoreIfEmpty()` + `accountRepository.syncAccountNames()`, toast on restore. The whole path is app-private: `performBackup()` always writes the JSON to `filesDir/backups/` (AutoBackupManager L60-63) before it attempts shared Documents, and `restoreIfEmpty()` reads that copy **first** (L191-196). Surviving a reinstall is Android Auto Backup's job, configured in `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API 28-30) — both include `domain="file" path="backups/"` and nothing else. **Verified end-to-end on an API 36 emulator:** seed JSON → `bmgr backupnow` → uninstall → install → fresh UID restored the file and the app rebuilt 14 transactions / 3 accounts / 27 categories with zero storage permissions granted.
  - The storage-permission gate and `StoragePermissionDialog` are **gone, and must not come back.** `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) and `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) are stripped from the requested-permission set on API 33+ — `dumpsys package` lists only READ_SMS/RECEIVE_SMS/POST_NOTIFICATIONS — so `checkSelfPermission()` returned DENIED forever: the dialog reappeared on **every** launch and `permissionLauncher.launch()` resolved to denied without ever showing a system prompt. Worse, `ensureDefaults()` only ran inside the granted/dismissed branches, so a user who swiped the dialog away got no default categories.
  - The permission that *would* reach a shared-Documents file written by a previous install is `MANAGE_EXTERNAL_STORAGE` (Android clears MediaStore ownership on uninstall, and `READ_EXTERNAL_STORAGE` never covered non-media files on API 30+). That is a Play "All files access" declaration; next to `READ_SMS` it is the last thing this app should request. Don't add it — Auto Backup plus the SAF importer in `SettingsScreen` (L181, `OpenDocument()`) cover both cases with no permission at all.
  - The older `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` branch was removed earlier for the same reason; don't re-add that either.
- `HisabApp` composable (L209–258): Scaffold + Box; `.haze(hazeState)` applied to NavHost modifier; `FloatingGlassmorphicBottomBar(currentDestination, hazeState, onNavigate)` overlaid BottomCenter. This is the ONLY Haze producer/consumer pair.

## 2. Navigation (ui/navigation/)

`Screen.kt`: sealed class, exactly 4 routes: `"dashboard"`, `"analytics"`, `"history"`, `"settings"`; companion `bottomNavItems`. `HisabNavHost.kt`: `startDestination = Screen.Dashboard.route`; single nav action `onSeeAllTransactions` Dashboard→History (`popUpTo(startDestination){saveState}, launchSingleTop, restoreState`). NO other routes exist.

## 3. Database (data/db/) — schema v8

`HisabDatabase.kt`: entities transactions/categories/budgets(recurring_rules)/accounts/pending_transactions. Builder: WAL journal, `.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)`, `.fallbackToDestructiveMigration(true)`, `SeedDatabaseCallback` seeds 3 accounts + 25 categories via raw SQL on create. `ensureDefaults()` (suspend) inserts same defaults if counts are 0 — called from MainActivity startup paths.

Migrations: 3→4 accounts.bankCode/accountLast4 + pending_transactions table + Investment INCOME→TRANSFER recategorization (+5 transfer categories); 4→5 idempotent re-run of the 3→4 alters/table-create; 5→6 pending_transactions.endingBalance; 6→7 accounts.lastKnownBalance/lastBalanceTimestamp + legacy Material icon names → emoji UPDATE map; **7→8 SMS provenance** — `sourceMessageHash`/`source`/`confidence`/`referenceNumber` on BOTH `transactions` and `pending_transactions`, plus `notificationPostedAt`/`notificationAttempts NOT NULL DEFAULT 0` on pending, plus `CREATE UNIQUE INDEX index_<table>_sourceMessageHash` on each. Per-statement `try{}catch{}` like 6→7. Historic rows keep `sourceMessageHash = NULL` (SQLite permits multiple NULLs under UNIQUE) — **never backfill**; a backfill would make the index reject real rows.

Entities (verbatim fields):
- `TransactionEntity` ("transactions"): FK categoryId→categories.id `SET_DEFAULT`; indices date/type/account + UNIQUE sourceMessageHash; defaults `account="Cash"`, `notes=""`, `createdAt=System.currentTimeMillis()`; provenance `sourceMessageHash`/`source`/`confidence`/`referenceNumber` all nullable.
- `CategoryEntity` ("categories"): UNIQUE composite index `(name, type)` — same name allowed across types; iconName/colorHex/isDefault/sortOrder.
- `AccountEntity` ("accounts"): UNIQUE index name; type String ("PRIMARY"/"SECONDARY"/"SAVINGS"/"CASH"); bankCode/accountLast4/lastKnownBalance/lastBalanceTimestamp nullable.
- `PendingTransactionEntity` ("pending_transactions"): type is String "DEBIT"/"CREDIT"; endingBalance nullable; same 4 provenance columns + `notificationPostedAt: Long?` and `notificationAttempts: Int = 0` (`@ColumnInfo(defaultValue="0")`). UNIQUE index on sourceMessageHash is the **sole** dedup authority (INV-2).
- `BudgetEntity` ("monthly_budgets") & `RecurringRuleEntity` ("recurring_rules"): **ORPHANED** — DAOs exist, zero repository/UI/receiver callers (verified by grep). Do not assume budget/recurring features exist.

`Converters.kt`: LocalDate ↔ epochDay (Long); TransactionType ↔ name string.

DAO highlights (TransactionDao.kt):
- `findMatchingManualTransaction(amount, type, minDate, account)`: `ABS(amount - :amount) < 1.0` ±₹1 reconciliation **scoped to `(source = 'MANUAL' OR source IS NULL)` and the account**, ORDER BY createdAt DESC LIMIT 1. The unscoped version matched the pipeline's own auto-logged rows and suppressed every second same-amount SMS — root cause #4. The `source IS NULL` arm keeps pre-v8 rows (all NULL) treated as hand-entered, which is what they were.
- `getBySourceHash(hash)`: cross-table dedup — a message already in history must not be re-claimable as a pending row.
- `getUserEnteredForAccountBetween(account, from, to)`: `(source = 'MANUAL' OR source IS NULL) AND sourceMessageHash IS NULL`. Feeds balance netting. The provenance filter is load-bearing: an SMS-derived row's own `AvlBal` was already written into `accounts.lastKnownBalance`, so netting it again would invent a discrepancy of the same size.
- `repairCorruptedTransferCategories()`: remaps TRANSFER rows whose categoryId points at non-transfer/missing categories onto 'Savings'.
- `getFilteredTransactions(...)`: SQL-side null-coalesced filters; account matches `account = :account OR (TRANSFER AND toAccount = :account)`.
- Raw projection classes `DailyTotalRaw`, `CategoryBreakdownRaw` declared at bottom of TransactionDao.kt.
- AccountDao.insert uses REPLACE on unique name → renaming conflicts silently replace.

PendingTransactionDao additions (v3.2.1): `insertClaim` (`OnConflictStrategy.IGNORE` → `-1` on hash conflict; **the only proof of a duplicate**), `deleteById(id): Int` (0 ⇒ lost race), `getBySourceHash`, `findInferredMarker(amount, accountLast4)`, `getUnnotified(minTimestamp, maxAttempts)`, `markNotified(id, postedAt)`, `markNotificationAttempted(id)`. Note `insert` is still REPLACE — writing a hash another row holds *deletes* that row, which is why callers guard with `takeIf { getBySourceHash(it) == null }`.

## 4. SMS pipeline (data/sms/)

**Shape since v3.2.1:** `SmsReceiver` (85L) and `SmsCatchUpSync` (74L) are thin adapters. Both call the single `TransactionProcessor` (644L), built by `buildTransactionProcessor(context)` in `AndroidSmsGateways.kt` — the pipeline's one construction site, since the project has no DI container. No gate, hash or dedup rule may live in an adapter; the duplicated MD5 helpers and nine silent `return`s that used to live in the receiver are gone.

Pure JVM (no Android imports, unit-tested): `SmsBankParser`, `BankAliasRegistry`, `SmsReference`, `SmsHash`, `SmsDecision`, `SmsGateways`, `TransactionProcessor`. `Context` enters only through `AndroidSmsGateways.kt`.

### SmsBankParser.parse(header, body) → ParsedBankSms? (SmsBankParser.kt, 432L)
Stage order: 0) header blacklist substring reject (`NON_BANK_BLACKLIST`: JIOFIBER, AIRTELFI, SWIGGY, ZOMATO, AMAZON, FLIPKART, IRCTC…); 1) DLT suffix `-P`/`-G` (promo/govt) reject; 1b) bank identify (delegates BankAliasRegistry; null → reject); 2) OTP/noise reject (`isOtpOrNoise`: otp, failed, declined, reversed, cancelled…; also balance-only bodies without credit/debit verbs); 3) amount extract (2 regexes, first match wins; ≤0 reject); 4) direction engine; 5) optional last-4 / merchant / endingBalance / **referenceNumber**.

Direction engine `determineTransactionDirection`: word-boundary verb regexes; proximity weights 4.0/2.5/1.5/0.8 by char-distance-to-amount (≤30/≤60/≤100/farther); VPA/UPI/purchase add +0.5 debit tie-breaker each occurrence; resolution: both-zero→null; creditScore>0 && ≥debit→CREDIT (credit-first precedence); debit>credit→DEBIT; fallback endingBalance>amount→CREDIT else DEBIT.

`ParsedBankSms` fields: amount, type("DEBIT"/"CREDIT"), bankName, senderHeader, accountLast4?, merchantOrPayee?, endingBalance?, **referenceNumber?**, rawBody.

### SmsReference.extract(body) → String? (SmsReference.kt, 55L)
Patterns for `Ref` / `UPI Ref No` / `RRN` / `Txn ID`, each tolerating an optional `:` and whitespace. Then **normalize** (strip label/separators, trim, uppercase, remove internal whitespace) and **validate**: length ≥ 6, alphanumeric only, and rejected if it contains `RS`, `INR`, `.` or `,` (an amount echo). Invalid ⇒ `null`, never a bogus identity, and the rejected text must never reach the hash. `Ref: 12`, `Ref: Rs. 500`, `Ref: ABC` are all rejected.

### SmsHash (SmsHash.kt, 174L) — one canonical identity
`canonical(parsed, senderHeader)` implements a three-tier hierarchy, first satisfied tier wins:

| Tier | Precondition | Key |
|---|---|---|
| 1 | valid ref **and** account known | `bankCode + normalizedAccount + reference` |
| 2 | valid ref, account unknown | `bankCode + reference` |
| 3 | no valid ref | `sender + amount + type + normalized FULL body` |

Tier 1 exists so the schema never depends on two accounts at one bank not sharing a reference. Tier 3 uses the **full** body (whitespace collapsed, case folded) — never `take(30)`, which is what made two genuine same-amount transactions collapse forever. `legacyBodyKey` reproduces the pre-v3.2.1 key so an upgraded install still recognises what it wrote before (the prefs file name is deliberately unchanged).

### SmsDecision.decide(parsed, ctx) (SmsDecision.kt, 135L)
Returns `Notify | AutoMerge | Suppress(reason)`. Fail-open by construction: `Suppress` only for a hand-entered match (`source='MANUAL'` + same account + window) or a recon key (amount + account + window), plus the CATCHUP-only proximity tiers. Unknown bank, unmatched account, zero linked accounts, `targetAccount == null` ⇒ `Notify`. A proven duplicate is **not** decided here — it comes from the DB claim.

### TransactionProcessor (TransactionProcessor.kt, 644L)
`process(senderHeader, rawBody, timestamp, origin): ProcessingOutcome` under a companion `Mutex`, so the live receiver and the catch-up scanner serialise instead of racing (root cause #9).

`ProcessingOutcome`: `Notified` · `ClaimedNotNotified` · `Merged` · `Duplicate` · `Suppressed(SuppressReason)` · `NotATransaction` · `Failed(Throwable)`. Every path returns one — a throw is an outcome, not a silent return.

Constants: `NOTIFICATION_RECOVERY_WINDOW_MS = 48h`, `MAX_NOTIFICATION_ATTEMPTS = 5`, `AUTO_MERGE_WINDOW_MS = 120_000`, `MANUAL_MATCH_LOOKBACK_DAYS = 1`, `CATCHUP_PROXIMITY_MS = 30min`, `BALANCE_DISCREPANCY_THRESHOLD = 1.0`, `BACKUP_TIMEOUT_MS = 5_000`.

Canonical order — **this ordering is the fix, not an implementation detail**:
`parse → gates → ATOMIC CLAIM → COMMIT → notification attempt → cache.mark → balance sync → time-boxed backup → diagnostics`.
1. Parse ⇒ `NotATransaction` when null.
2. Account resolution: advisory only, never a drop (root cause #2 was an asymmetric gate).
3. Cache `peek` ⇒ `Suppressed(CACHE_HIT)` — a shortcut, never authority (INV-1).
4. Hand-entered match (`source='MANUAL'`, ±₹1, 24 h, same account) ⇒ `Suppressed(MANUAL_MATCH)`.
5. Recon key consume ⇒ `Suppressed(RECON_KEY)`.
6. CATCHUP-only proximity tiers (±30 min pending/txn; 3-day-net vs endingBalance) ⇒ `Suppressed(CATCHUP_PROXIMITY)`.
7. `claimAndMaybeMerge` in ONE transaction: `insertClaim` → `-1` ⇒ `Duplicate`; `transactionDao.getBySourceHash` non-null ⇒ delete the claimed row ⇒ `Duplicate` (closes the cross-table hole); auto-merge when an opposite-direction pending from a **different bank** sits inside 120 s (`deleteById == 0` ⇒ lost race, keep own claim and notify).
8. Notify **immediately** after the commit, then `markNotified`.
9. `cache.mark` — post-commit tail only (INV-7). Never on a `Duplicate`, `Suppressed` or lost-race exit.
10. `syncAccountBalance`: monotonic guard (an SMS older than `lastBalanceTimestamp` is ignored), always refreshes `lastKnownBalance`/`lastBalanceTimestamp` on both origins including after a merge (the shipped merge path did not, which manufactured false "missed transaction" alerts). Discrepancy ≥ ₹1 after netting ⇒ INFERRED marker pending (`hash=null`, `source=BALANCE_RECONCILIATION`, `confidence=INFERRED`, deduped via `findInferredMarker`) + `postMissedTransaction`.
11. `netUserEnteredActivity`: nets only rows the *user* originated (`getUserEnteredForAccountBetween`) between `lastBalanceTimestamp` and now. Netting can only shrink a discrepancy — the conservative direction for an inference.
12. Backup last, `withTimeoutOrNull(BACKUP_TIMEOUT_MS)` — root cause #6 was `performBackup()` sitting *between* the commit and the notify inside a ~10 s `goAsync` budget.
13. Diagnostics last of all, at the single funnel in `process()`, wrapped in `runCatching`.

`recoverUnnotified(): List<Long>` — the crash-after-claim path. Selects `notificationPostedAt IS NULL AND confidence='CONFIRMED' AND source != 'BALANCE_RECONCILIATION' AND timestamp > now − 48h AND notificationAttempts < 5`, re-posts (deterministic id ⇒ replace, not duplicate), marks on success, increments attempts on failure. Called from **MainActivity**, not catch-up: recovery must not require `READ_SMS`. Past either INV-6 bound a row stops retrying but stays valid and dashboard-visible.

### Gateways (SmsGateways.kt 111L, AndroidSmsGateways.kt 224L)
`SmsHashCache` (INV-1/INV-7 doc-commented) · `SmsClock` (injectable time) · `AtomicDb` · `SmsNotifier` (returns *attempted successfully*, so a denied POST_NOTIFICATIONS is retryable, not terminal) · `BackupTrigger` · `SmsDiagnosticsLog` + `SmsDiagnosticEntry`.
Android impls: `PrefsSmsHashCache` (same `"sms_processed_hashes"` file as before — renaming it would orphan every key and make one upgrade re-notify the whole catch-up window), `RoomAtomicDb` (`withTransaction`), `RealSmsNotifier` (permission + channel gated, returns false rather than throwing), `AutoBackupTrigger`, `PrefsSmsDiagnosticsLog`.

`PrefsSmsDiagnosticsLog`: 50-entry JSON ring buffer in its own `"sms_diagnostics"` prefs file, newest first, process-wide lock, corrupt JSON ⇒ empty. Prefs rather than Room because it must be writable from a receiver about to be killed, must not contend with the pipeline's own transaction, and must be readable when the database is the broken thing. Records ts/sender/amount/origin/outcome/reason — **no raw body, no reference, no balance**. Viewer: Settings → About → tap the version line 5×.

### SmsReceiver (85L)
Multi-PDU concat → `goAsync()` → `handleSms(context, sender, body, timestamp)` (`internal`, companion object — the E2E seam an instrumented test drives) → `buildTransactionProcessor(context).process(..., SmsOrigin.REALTIME)`; outer catch logs; `finally pendingResult.finish()`. That is the whole file.

### SmsCatchUpSync.runSync(context) (74L) — called from MainActivity LaunchedEffect
`READ_SMS` guard → `content://sms/inbox` last 24 h DESC → `processor.process(sender, body, smsDate, SmsOrigin.CATCHUP)` per message. **It now notifies**, inheriting the processor's tail; previously it inserted pending rows silently and, because its hash key was byte-identical to the receiver's and written to the same prefs file, it could consume a message the receiver was about to notify about (root cause #9).

### SmsNotificationHelper (630 lines)
Channel `bank_transactions_channel` IMPORTANCE_HIGH. Notification id scheme `(pending.id * 1000L).toInt() and 0x7FFFFFFF` (deterministic ⇒ a recovery re-post replaces rather than duplicates). Actions (string constants): ACTION_LOG_TRANSACTION, ACTION_SWAP_TRANSFER_ACCOUNTS, ACTION_SWAP_CREDIT_TRANSFER, ACTION_LOG_INWARD_TRANSFER, ACTION_PAGINATE_NOTIFICATION, ACTION_DISMISS_NOTIFICATION, ACTION_UNDO_AUTO_MERGE, ACTION_SELECT_EXPENSE_CATEGORY, ACTION_SELECT_INCOME_CATEGORY.

- Stage 1 `postBankTransactionNotification`: DEBIT → [💸 Expense][⇄ Transfer][❌ Dismiss]; CREDIT → [💰 Income][⇄ Transfer In][❌ Dismiss]; PRIORITY_MAX; content tap opens MainActivity.
- Stage 2 `postCategoryPickerNotification`: pages of 2 categories of requested type (modulo total), buttons labeled `"$emoji $name"`, [🔄 More...] paginates via ACTION_PAGINATE_NOTIFICATION.
- `swapToCreditTransferAccountsNotification`: source-account picker excluding accounts whose bankCode equals credited bank (equals ignoreCase against FULL NAME — bankCodes are short codes like "BOB" so this filter rarely excludes anything; quirk).
- `postAutoMergeSuccessNotification`: id `(txId xor currentTimeMillis)`; timeoutAfter 5000; [↩️ Undo].
- `postSuccessNotification`: timeoutAfter 3000.
- `postMissedTransactionNotification`: BigTextStyle, copy = "⚠️ ₹X of unlogged activity" and explicitly says the figure is a **net** that "may be one transaction or several". No count, direction or merchant may be implied — and it carries no quick-log action, precisely because there is nothing safe to default to.
- `getCategoryEmoji(iconName)`: blank→"📋"; any char code >127 / surrogate / OTHER_SYMBOL → return trimmed input AS-IS (emojis pass through); else legacy Material-name→emoji when-table (~60 entries); unknown → `iconName.take(2)`. Pure logic despite Android-heavy file.

### NotificationActionReceiver (386 lines)
Routes every action via goAsync + IO scope. Key behaviors:
- SELECT_*_CATEGORY → stage 2 page 0. PAGINATE → mode switch ("TRANSFER_CREDIT"→account picker; "EXPENSE"/"INCOME"→category picker page N; else legacy stage 1).
- DISMISS (swipe deleteIntent) → cancel notification ONLY; pending row intentionally kept.
- UNDO_AUTO_MERGE → delete tx by EXTRA_TX_ID if exists, performBackup, success toast-notification.
- LOG_INWARD_TRANSFER / LOG_TRANSACTION: both now materialise the row inside `db.withTransaction` (INV-3) carrying `sourceMessageHash`/`source` forward with `confidence=MANUAL`, so a crash can no longer duplicate the transaction or destroy the pending row with nothing to show for it. `recon_*` keys are scoped by account+timestamp. Account matching still uses `bankCode.equals(fullBankName)` and falls back to primary/first (pre-existing quirk, unchanged).

## 5. Repositories (data/repository/)

- TransactionRepository (254L): thin DAO wrapper; EVERY insert/update/delete triggers `autoBackupManager?.performBackup()`. Analytics helpers: getMonthlyTrend (N sequential per-month queries), getWeekdayAverages (divides by distinct weekdays capped at LocalDate.now()), getCategoryTrend (top-N chosen from END MONTH only; map keyed by categoryId.toString()). `repairCorruptedTransferCategories()` swallows exceptions.
- AccountRepository (122L): rename overload propagates via transactionDao.updateAccountName + updateToAccountName; setPrimaryAccount loops N updates (no transaction); `syncAccountNames()` (startup): rewrites stale transaction account names via hardcoded legacy mapping primary←{"Primary Bank","Cash","primary"}, 2nd←{"Secondary Bank","secondary"}, 3rd←{"Savings Bank","savings"}.
- BackupRepository (122L): `exportReport(uri, format, targetMonth?)` → fresh ReportGenerator; `smartImport` → AutoBackupManager.smartImportFromDocuments; `importBackup(uri)`: content starting "{" → restoreFromJson (success count = TOTAL tx count post-import, not rows added), else CSV import path builds missing categories with hardcoded Material name `"MoreHoriz"`/`#607D8B`.
- SpendingLimitRepository (68L): DataStore file **"hisab_limit_settings"**, keys limit_type/limit_amount/limit_date/limit_enabled; enum LimitType DAILY/WEEKLY/MONTHLY/SPECIFIC_DAY; data class SpendingLimitConfig(type=DAILY, amount=1000.0, specificDate=today, isEnabled=false); clearLimit only flips enabled=false.
- CategoryRepository (45L).
- PendingTransactionRepository (77L) — ctor is now `(pendingDao, transactionDao, db, autoBackupManager?)`. Plain reads/inserts still trigger no backup (AutoBackupManager snapshots pendings directly), but `approve(pending, transaction): Boolean` is the INV-3 primitive: inside `db.withTransaction` it re-reads the pending row (**gone ⇒ returns `false`**, which is the double-tap / notification-action-got-there-first guard), deletes it, and inserts the transaction carrying `sourceMessageHash` (guarded by `takeIf { transactionDao.getBySourceHash(it) == null }` because `insert` is REPLACE), the pending row's own `source`, `confidence = MANUAL`, and `referenceNumber`. The backup runs **after** the commit, never inside it. Note `source` is deliberately *not* rewritten to `MANUAL`: `findMatchingManualTransaction` suppresses a new SMS when a manual row matches, so stamping approved SMS rows `MANUAL` would make every approval suppress the next same-amount SMS on that account — root cause #4 rebuilt one layer up.

## 6. Backup system (data/backup/)

AutoBackupManager (549L). `BACKUP_VERSION=6` (bumped for the v8 provenance fields — serialize emits `sourceMessageHash`/`source`/`confidence`/`referenceNumber` plus pending `notificationPostedAt`/`notificationAttempts`; restore stays tolerant via `opt*`, so a v5 file still imports), `MAIN_BACKUP_NAME="hisab_auto_backup.json"`.
- `performBackup()`: full 4-table serialize → ALWAYS writes internal `filesDir/backups/hisab_auto_backup.json` → best-effort copies to cartesian product {Public Documents, Public Downloads, /storage/emulated/0/Documents, /storage/emulated/0/Download, app-external Documents, app-external root} × {"Hisab", ""} (≤12 files, per-copy empty catches) → MediaStore upsert under Documents/Hisab/ → updateLastBackupTime(). Runs after EVERY mutation (write amplification).
- Restore scan order: smartImportFromDocuments scans Documents/Hisab → Documents → Downloads/Hisab → Downloads → hardcoded /storage/emulated/0/* and /sdcard/* paths → app-external dirs; accepts ANY *.json (or exact name) containing literal `"transactions"`; then MediaStore query DATE_MODIFIED DESC. `restoreIfEmpty()` prefers INTERNAL copy first, only when transactions table empty.
- `restoreFromJson`: requires "transactions" array; optional accounts/categories/pendingTransactions; account match by name→same-type→insert (bankCode/last4 preserved when import null); category dedupe `${name}_${type}` (missing → MoreHoriz/#607D8B defaults); tx fingerprint includes createdAt → identical logical rows re-import duplicate; category fallback chain map-miss→first-of-type→id 1L; NOT atomic. JSON checksum SHA-256 computed but NEVER verified; BACKUP_VERSION never checked.
- BackupPreferences: DataStore **"hisab_auto_backup_prefs"**, auto_backup_enabled (default TRUE), last_backup_time.

## 7. Export/Import (data/export/ReportGenerator.kt, 525L)

ExportFormat enum: PDF/XLSX/CSV/JSON (displayNames verbatim). `generateReport(uri, format, transactions, categories, targetMonth?, currentBalances: Map<String,Double> = emptyMap())`: filters by month when targetMonth!=null; sorts ASC oldest-first; returns count. `currentBalances` MUST be computed from the FULL ledger (BackupRepository does it via `SplitAccounting.accountBalance`) — a month-scoped row list cannot produce a current balance.
- PDF: A4 PdfDocument canvas, 24 ledger rows/page, KPI row 1 = PERIOD METRICS via `FinancialSummary.of` (Total Income #10B981, Gross Expenses #EF4444, Net Expenses #F97316, Transfer Activity #8B5CF6), KPI row 2 = "Account Balances (Current)" (up to 3 accounts + Combined Balance, #3B82F6 — only when `currentBalances` non-empty). Ledger column "Transfer" (was mislabeled "Savings"), footer "TOTALS (PERIOD)" = gross debit / credit / transfer-volume sums. Dates `dd MMM yyyy`, chart page always appended. Historical note: pre-fix KPI cards showed "Total Savings" = SUM of all TRANSFER amounts (transfer VOLUME, not a balance) and "Net Balance" = monthly income−expense — both were concept errors, fixed 2026-08-28.
- XLSX (POI XSSFWorkbook): sheet "Ledger & Transactions", headers Date/Type/Subtype/Category/Account/To Account/Debit (Expense)/Credit (Income)/Transfer/Notes ("Transfer" was "Savings"); numeric amounts with 0.0 placeholders.
- CSV: hand-rolled 10-column writer, naive escaping (does NOT double embedded quotes — corrupts on quotes/newlines). **CRITICAL: incompatible with importer** — CsvHelper.csvToTransactions expects legacy 6 columns `Date,Type,Category,Amount,Account,Notes` (amount at index 3); importing an app-exported CSV silently drops every row.
- JSON: delegates to AutoBackupManager.exportBackupString() (raw rows, no arithmetic).

CsvHelper (108L): RFC-style escaping writer; reader state-machine cannot handle doubled `""` or multiline quoted values; malformed rows silently skipped; account default "Cash".

## 8. Models & utils (pure JVM unless noted)

- TransactionType {INCOME, EXPENSE, TRANSFER}; MonthlySummary(totalIncome,totalExpense,netBalance,transactionCount); DailyTotal(date,totalAmount); CategoryBreakdown(categoryId,categoryName,colorHex,iconName,totalAmount,percentage,transactionCount).
- **`util/SplitAccounting` = THE authoritative account-balance layer.** `accountBalance(name, rows)` is account-SCOPED (only `tx.account == name` rows contribute income/expense/split; transfers move both legs). The pre-2026-08-28 version fed every account the GLOBAL income/expense totals — the dashboard's Accounts Overview showed Secondary ₹3,022.63 / Savings ₹6,019.33 while the true values were ₹0 / ₹2,501.70. Also: `isSavingsAccount(AccountEntity)`/`isSavingsAccountName` (name contains "saving" OR type=="SAVINGS"), `primaryPlusSecondaryBalance(map)` (hero card: non-savings sum, grand-total fallback). `util/FinancialSummary.of(rows, currentBalances, accountNames)` = the ONE statement model: period metrics (income/gross/split/net/transfer-activity) kept strictly apart from current account state. Every balance consumer (Dashboard, Analytics, BackupRepository→PDF) must call these — never re-derive.
- CurrencyFormatter: en-IN grouping `#,##,##0.00`; negative uses Unicode MINUS U+2212 (`−₹…`) — beware string matching; formatCompact Cr/L/K thresholds 10M/100K/1K (Locale.US decimals).
- DateUtils: English-only formatters; formatRelative Today/Yesterday literals (PDF export deliberately avoids them); daysElapsed: current→today.dayOfMonth, past→full length, future→0.
- CategoryIconMapper (200L, Compose-dependent): `getIcon(iconName)` maps MATERIAL names→ImageVector, else Icons.Filled.MoreHoriz — **DEAD CODE, never called** (verified grep: only `getAccountIcon` used, in `AccountsOverviewWidget` + `SettingsScreen`). getAccountIcon: cash→Payments, card|credit→CreditCard, upi|wallet|pay→AccountBalanceWallet, else AccountBalance. The stale unused imports in ExpenseLeaderboard/TopCategorySpendWidget/TransactionItem have been removed — those three files no longer reference the class at all.

Icon rendering truth: UI displays emojis via `SmsNotificationHelper.getCategoryEmoji(category.iconName)` (SettingsScreen tiles, CategoryPicker, ExpenseLeaderboard, TopCategorySpendWidget, TransactionItem, CategoryEditDialog). DB stores emojis (post 6→7 migration; CategoryEditDialog offers 50 preset emojis + free-text input); legacy Material names still resolve via the mapping table. Both systems coexist by design.

## 9. ViewModels & state pattern

Uniform: MutableStateFlow + flatMapLatest/combine + `stateIn(SharingStarted.WhileSubscribed(5000))`; UI collects via collectAsState(); no LiveData anywhere.
- DashboardViewModel (454L): init repairs corrupted transfer categories; flows incl. accountBalances via `SplitAccounting.accountBalance` (account-scoped; TRANSFER moves account→toAccount), hero `primaryAndSecondaryBalance` = `SplitAccounting.primaryPlusSecondaryBalance` (current state, NOT month-scoped), `savingsAccountBalance` (hero SAVINGS tile) = savings account's CURRENT balance via the same util (was the month's net transfer delta — coincidence that it matched); savings detection centralized in SplitAccounting (name OR type); spendingLimitStatus warning ≥75%, exceeded >100%. Pending-row handling (v3.2.1):
  - `linkedAccounts: StateFlow<List<AccountEntity>>` — full entities, because the SMS-to-account match needs `bankCode`/`accountLast4`, which the name-only `accounts` flow cannot supply.
  - `resolveAccountName(pending, candidates)`: `bankCode.equals(bankName, ignoreCase)` **or** matching `accountLast4`, then primary, then first, then `"Primary Bank"`. Approving from the dashboard used to fall straight through to primary, so a BOB debit landed on the primary account whenever the user tapped the card instead of the notification.
  - `approvePendingTransaction(pending, categoryName)`: category by name+type → first-of-type → id 1L; date is **the SMS's own timestamp**, not today (a catch-up scan can surface a 24 h-old message, which used to be filed on the wrong day); delegates to `PendingTransactionRepository.approve` for atomicity.
  - `logInferredActivity(pending, transaction)`: the inferred-row path — the user writes the transaction that explains a balance gap and the marker is consumed in the same step. Uses the same `approve`, which preserves `source = BALANCE_RECONCILIATION` and therefore keeps the row out of the netting query (the movement is already inside `lastKnownBalance`; re-netting it would manufacture an identical fresh discrepancy).
- HistoryViewModel (159L): server-side date filter, in-memory type/account/search filtering (search matches category/notes/raw amount/formatted currency/accounts).
- AnalyticsViewModel (482L): loads ALL transactions 2020-01-01..2030-12-31 into memory; enums CategoryFilterType{ALL,INCOME,EXPENSE,TRANSFERS} (default EXPENSE), BarChartTimeFilter{TODAY,SPECIFIC_DATE,WEEKLY,FIFTEEN_DAYS,MONTHLY} (default MONTHLY); all account balances (primary/selected/savings) delegate to `SplitAccounting.accountBalance` (its private `calculateBalanceForAccount` was deleted 2026-08-28 — it treated SPLIT_REIMBURSEMENT as −balance); public mutable selectedSecondaryAccountName/barChartFilter/barChartSpecificDate StateFlows.
- SettingsViewModel (215L): DataStore "hisab_settings" declared here; availableExportMonths grouped from all transactions desc; export success msg "Exported $count records ($scopeText) as ${format.displayName} successfully".

## 10. Screens/components/charts (key facts)

- SettingsScreen (1811L): sections = Accounts Management, collapsible Income/Expense/Transfer category accordions (grid tiles of 4, emoji via getCategoryEmoji, colorHex via android.graphics.Color.parseColor try/catch), Data & Backup card (auto-backup Switch reading BackupPreferences directly, Export Report → ExportFormatDialog [All Records vs Specific Month radio + per-month txn counts], Import Backup → smartImport with SAF OpenDocument fallback mime array ["application/json","text/csv","text/comma-separated-values","*/*"], App Info & Restricted Settings), About card whose version line now reads `util/AppVersion.name(context)` / `.code(context)` from `PackageManager` — **no longer hardcoded**, because AGP 9 does not generate `BuildConfig` for this module. Tapping that line `VERSION_TAPS_FOR_DIAGNOSTICS = 5` times reveals an **SMS Auto-Logging Diagnostics** item that opens `SmsDiagnosticsDialog` (reads `PrefsSmsDiagnosticsLog.recent()` off the main thread in a `LaunchedEffect(reloadToken)`; two lines per entry; green for NOTIFIED/MERGED/RECOVERED, red for FAILED/CLAIMED_NOT_NOTIFIED/RECOVERY_FAILED; "Clear log" clears on IO then bumps the token on the caller's dispatcher, since the token is Compose state). SAF launchers CreateDocument/OpenDocument; filenames `Hisab_Statement_${MMM yyyy}.ext` or `Hisab_Statement_All_Records_${yyyy-MM-dd}.ext`. Toasts for export/import results; SnackbarHost present but unused.
- PendingTransactionsCard (269L): accent is amber `InferredAccent = 0xFFF59E0B` when `confidence == INFERRED` (`BALANCE_RECONCILIATION` is its only producer), otherwise the credit/debit colours. An inferred row shows an **unsigned** amount followed by "of unlogged activity", an "Inferred from balance" badge, and a **Review** CTA instead of the one-tap category approve — because the figure is a *net*, so no sign, count, direction or merchant may be implied, and there is no category safe to default to. Confirmed rows keep the existing approve/dismiss actions.
- BankSelectionSheet (284L): module-level `ALL_INDIAN_BANKS` (46 entries incl. PYTM/AIRTEL/JIO payment banks); Save button requests RECEIVE_SMS+READ_SMS(+POST_NOTIFICATIONS on 33+) via RequestMultiplePermissions BEFORE saving mapping. The ONLY runtime permission request in the entire UI layer.
- QuickAddSheet (532L): keypad constraints (single dot, ≤2 decimals); TRANSFER requires ≥2 distinct accounts; date picker ↔ LocalDate via systemDefault zone; nested AddAccountDialog drops bankCode/last4. `initialAmount: Double?` / `initialAccount: String?` prefill the sheet — this is what the inferred row's **Review** action opens, prefilled with the discrepancy and the account whose balance revealed it, category left for the user.
- DashboardScreen: end-of-month banner when dayOfMonth >= lengthOfMonth−1; recent = first 10 of month flow; fallback category literals "#607D8B"/"MoreHoriz".
- Components with hardcoded colors: income 0xFF00E676, expense 0xFFFF5252, transfer 0xFF64B5F6 recur across FilterBar/QuickActionsRow/TransactionItem/HistoryScreen/AnalyticsScreen.
- Charts are ALL custom Canvas (Vico dependency exists but charts don't use it): DonutChart (ModernDonutPalette 12 colors, 18° gaps, MoM chip), AnalyticsLineChart (mode EXPENSE/INCOME/BOTH), UnifiedBarChart (imports analytics ViewModel enums — chart↔VM coupling), SpendingHeatmap (Monday-first, min-max normalization over nonzero days), WeekdayBarChart (dashed average line). UNUSED charts: CategoryTrendChart, DailyLineChart, IncomeExpenseBarChart, WeekdayBarChart (no screen references found).
- Unused components: SummaryCard; `AutoRestoreLoadingDialog` IS used (MainActivity); NumericKeypad.onClear param never invoked internally. `StoragePermissionDialog.kt` was **deleted** — its only caller was the unsatisfiable storage gate in MainActivity (see §1).

## 11. Theme

Theme.kt: `LocalHisabColors` staticCompositionLocalOf with HisabExtendedColors (income/expense/warning surfaces, chartColors 8, heatmap ramps dark+light); accessor `HisabTheme.colors`. Color.kt palette: PrimaryTeal 0xFF00E5A0, backgrounds DarkBackground 0xFF0F0F14/LightBackground 0xFFF8F9FC. Type.kt: downloadable Google Fonts "Outfit"+"Inter" via Play Services provider (font_certs.xml) — the only network-fetched asset family (silently falls back offline).

## 12. Tests

`app/src/test` — **90 tests across 7 classes**, plain JUnit4 + `sqlite-jdbc`. No mockk/mockito and **no `kotlinx-coroutines-test`**; suspend functions are driven with plain `runBlocking`. Gradle caches a green run, so use `--rerun-tasks` and read the real counts from `app/build/test-results/testDebugUnitTest/*.xml` headers rather than trusting console output.

| Class | Tests | Covers |
|---|---|---|
| `SmsBankParserTest` | 12 | the real user-reported BOB fixtures (₹40/₹45/₹30), 7-bank alias matching, word-boundary cases ("Dear"/"card"/"drive" must not trigger), JIOFIBER rejection, JIOPB acceptance, ATM withdrawal, `getCategoryEmoji` direct + legacy mapping, reference extraction |
| `SmsHashTest` | 15 | the three identity tiers, same-ref-different-account ⇒ different hashes, ref normalization + the rejection table (`Ref: 12`, `Ref: Rs. 500`, `Ref: ABC` ⇒ null **and** rejected text absent from the key), full-body tier-3 stability |
| `SmsDecisionTest` | 15 | fail-open (unknown bank / no linked account / `targetAccount == null` ⇒ Notify), manual-match and recon-key suppression, CATCHUP-only proximity tiers |
| `TransactionProcessorTest` | 41 | ordering journal (`tx-begin → claim → commit → notify → mark → backup → diag`), INV-2 duplicate `-1`, INV-1 cache-not-authority, INV-7 no mark without a commit, auto-merge **false-positive** set, crash-after-claim recovery, INV-6 attempt/age caps, balance netting, diagnostics-as-witness |
| `MigrationV7ToV8Test` | 2 | `MIGRATION_7_8` against a real populated v7 SQLite fixture — the only tests that execute actual SQL |
| `LastGraphemeTest` | 4 | `lastGrapheme` in the add-category emoji tile — surrogate pairs, variation selectors, second-tap replacement (a mid-emoji slice would store a fragment that renders as a box on the dashboard, in history and in the notification) |
| `ExampleUnitTest` | 1 | trivial |

`androidTest`: `ExampleInstrumentedTest` only. `SmsPipelineFakes.kt` supplies JVM fakes for every gateway and DAO the processor touches; DAO methods the processor does **not** use throw rather than returning a plausible empty value, so a future change that starts calling one fails loudly.

⚠️ **Coverage gap, stated plainly:** the fakes *mirror* DAO semantics (`insertClaim`'s IGNORE-on-UNIQUE, `getUnnotified`'s filters, the `(source = 'MANUAL' OR source IS NULL)` provenance filters) rather than executing the real `@Query` strings. A typo inside a production query would leave all of those tests green. Device-level coverage lives in `HARDENING_CHECKLIST.md`.

## 13. Master gotcha list (all verified)

1. Room: destructive fallback + hand-written migrations — new entity changes MUST add migration (see AGENTS.md).
2. Every tx/account/category mutation = FULL backup rewrite to ≤13 locations (write amplification; empty catches hide failures).
3. Two incompatible CSV dialects: exported 9-col CSV cannot be re-imported (legacy 6-col only). JSON import ignores filters entirely.
4. `sms_processed_hashes` SharedPreferences entries never expire, and that is now **deliberate and harmless**: since v3.2.1 the cache only ever *skips* work (INV-1) and Room's `UNIQUE(sourceMessageHash)` is what actually decides (INV-2). It was harmful before, when the cache *was* the authority and its key used `rawBody.take(30)`. Two rules: never write a key before the Room claim commits (INV-7 — a mark plus a rolled-back claim loses the message forever), and never rename the prefs file (every key would orphan and one upgrade would re-notify the whole catch-up window).
5. NotificationActionReceiver matches accounts via `bankCode.equals(fullBankName)` — short codes never equal full names; falls back to primary/first silently. `DashboardViewModel.resolveAccountName` is the fixed version (bankCode **or** last4, then primary/first); the receiver still carries the old quirk.
6. Backup JSON checksum/version written but never validated; restore not atomic.
7. Hardcoded fallback triple ["Primary Bank","Secondary Bank","Savings"] recurs in 3 ViewModels + DB seed; savings detection = substring "saving".
8. Version strings are read from `PackageManager` via `util/AppVersion.kt` (AGP 9 generates no `BuildConfig` here) — do not reintroduce literals. `versionCode`/`versionName` are the single source of truth in `app/build.gradle.kts`; README prose still lags.
9. Orphaned features: budgets + recurring rules (tables/DAOs only); dead code: CategoryIconMapper.getIcon, SummaryCard, 4 chart composables.
10. CurrencyFormatter negative sign U+2212 breaks naive string comparisons/search.
11. AnalyticsViewModel holds all transactions (2020–2030) in memory.
12. No INTERNET permission (offline-first enforced); explicit intents are the GitHub profile link and App Info settings only — the `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` intent was removed with the dead all-files flow.
13. **`notificationPostedAt != null` means "post() returned without throwing", never "the user saw it."** Permission and channel state are outside the app's control, which is why INV-4 promises exactly-once *processing* plus a bounded notification *attempt* — never "exactly one notification". Do not tighten that wording anywhere in the docs or the code comments.
14. Auto-merge is the one place this hardening could make things **worse**: an unrelated same-amount debit and credit must stay two rows, never become an invented transfer. Merge criteria were carried over unchanged on purpose, and `TransactionProcessorTest`'s false-positive set exists to pin that parity. A failure there is a merge-criteria bug to investigate, not a test to relax.
15. The diagnostics log is a **witness, never a participant**: it is written at the single funnel at the end of `process()`, inside `runCatching`. Never move a `record(...)` call earlier, and never let its failure alter an outcome.
