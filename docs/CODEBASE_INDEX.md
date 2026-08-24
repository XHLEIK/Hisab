# Hisab Codebase Index (verified)

Every fact below was verified against source. Line numbers refer to current files and may drift after edits; identifiers are verbatim. Companion quick-guide: root `AGENTS.md`.

## 1. Entry flow & wiring (no DI)

`HisabApplication.kt` — lazy singleton `database` (`HisabDatabase.getDatabase(this)`).

`MainActivity.kt` `onCreate` (L65–206): manually constructs `AutoBackupManager(applicationContext, database)` then `TransactionRepository(transactionDao, autoBackupManager)`, `CategoryRepository`, `AccountRepository(accountDao, transactionDao, autoBackupManager)`, `BackupRepository(...)`, `PendingTransactionRepository(pendingTransactionDao)`. All passed down through `setContent { HisabApp(...) } → HisabNavHost(...)`.

Startup side effects in MainActivity:
- `LaunchedEffect(Unit)` L131–165: launches `SmsCatchUpSync.runSync()` on IO; checks storage permission; if granted runs `database.ensureDefaults()` + `autoBackupManager.restoreIfEmpty()` + `accountRepository.syncAccountNames()` (toast on restore); else shows `StoragePermissionDialog`.
- Permission grant path L82–129 requests READ/WRITE_EXTERNAL_STORAGE and (API 30+) fires `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` intent (`Environment.isExternalStorageManager()`).
- `HisabApp` composable (L209–258): Scaffold + Box; `.haze(hazeState)` applied to NavHost modifier; `FloatingGlassmorphicBottomBar(currentDestination, hazeState, onNavigate)` overlaid BottomCenter. This is the ONLY Haze producer/consumer pair.

## 2. Navigation (ui/navigation/)

`Screen.kt`: sealed class, exactly 4 routes: `"dashboard"`, `"analytics"`, `"history"`, `"settings"`; companion `bottomNavItems`. `HisabNavHost.kt`: `startDestination = Screen.Dashboard.route`; single nav action `onSeeAllTransactions` Dashboard→History (`popUpTo(startDestination){saveState}, launchSingleTop, restoreState`). NO other routes exist.

## 3. Database (data/db/) — schema v7

`HisabDatabase.kt`: entities transactions/categories/budgets(recurring_rules)/accounts/pending_transactions. Builder L224–233: WAL journal, `.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)`, `.fallbackToDestructiveMigration(true)`, `SeedDatabaseCallback` seeds 3 accounts + 25 categories via raw SQL on create. `ensureDefaults()` (suspend) inserts same defaults if counts are 0 — called from MainActivity startup paths.

Migrations: 3→4 accounts.bankCode/accountLast4 + pending_transactions table + Investment INCOME→TRANSFER recategorization (+5 transfer categories); 4→5 idempotent re-run of the 3→4 alters/table-create; 5→6 pending_transactions.endingBalance; 6→7 accounts.lastKnownBalance/lastBalanceTimestamp + legacy Material icon names → emoji UPDATE map.

Entities (verbatim fields):
- `TransactionEntity` ("transactions"): FK categoryId→categories.id `SET_DEFAULT`; indices date/type/account; defaults `account="Cash"`, `notes=""`, `createdAt=System.currentTimeMillis()`.
- `CategoryEntity` ("categories"): UNIQUE composite index `(name, type)` — same name allowed across types; iconName/colorHex/isDefault/sortOrder.
- `AccountEntity` ("accounts"): UNIQUE index name; type String ("PRIMARY"/"SECONDARY"/"SAVINGS"/"CASH"); bankCode/accountLast4/lastKnownBalance/lastBalanceTimestamp nullable.
- `PendingTransactionEntity` ("pending_transactions"): type is String "DEBIT"/"CREDIT"; endingBalance nullable.
- `BudgetEntity` ("monthly_budgets") & `RecurringRuleEntity` ("recurring_rules"): **ORPHANED** — DAOs exist, zero repository/UI/receiver callers (verified by grep). Do not assume budget/recurring features exist.

`Converters.kt`: LocalDate ↔ epochDay (Long); TransactionType ↔ name string.

DAO highlights (TransactionDao.kt):
- `findMatchingManualTransaction(amount, type, minDate)` L33–34: `ABS(amount - :amount) < 1.0` ±₹1 reconciliation, ORDER BY createdAt DESC LIMIT 1.
- `repairCorruptedTransferCategories()` L166–167: remaps TRANSFER rows whose categoryId points at non-transfer/missing categories onto 'Savings'.
- `getFilteredTransactions(...)` L118–138: SQL-side null-coalesced filters; account matches `account = :account OR (TRANSFER AND toAccount = :account)`.
- Raw projection classes `DailyTotalRaw`, `CategoryBreakdownRaw` declared at bottom of TransactionDao.kt.
- AccountDao.insert uses REPLACE on unique name → renaming conflicts silently replace.

## 4. SMS pipeline (data/sms/)

### SmsBankParser.parse(header, body) → ParsedBankSms? (SmsBankParser.kt L160–212)
Stage order: 0) header blacklist substring reject (`NON_BANK_BLACKLIST` L108–118: JIOFIBER, AIRTELFI, SWIGGY, ZOMATO, AMAZON, FLIPKART, IRCTC…); 1) DLT suffix `-P`/`-G` (promo/govt) reject; 1b) bank identify (delegates BankAliasRegistry; null → reject); 2) OTP/noise reject (`isOtpOrNoise` L301: otp, failed, declined, reversed, cancelled…; also balance-only bodies without credit/debit verbs); 3) amount extract (2 regexes L121–124, first match wins; ≤0 reject); 4) direction engine; 5) optional last-4 / merchant / endingBalance.

Direction engine `determineTransactionDirection` L221–281: word-boundary verb regexes; proximity weights 4.0/2.5/1.5/0.8 by char-distance-to-amount (≤30/≤60/≤100/farther); VPA/UPI/purchase add +0.5 debit tie-breaker each occurrence; resolution: both-zero→null; creditScore>0 && ≥debit→CREDIT (credit-first precedence); debit>credit→DEBIT; fallback endingBalance>amount→CREDIT else DEBIT.

`ParsedBankSms` fields: amount, type("DEBIT"/"CREDIT"), bankName, senderHeader, accountLast4?, merchantOrPayee?, endingBalance?, rawBody. Pure JVM (only java.util.regex) — unit-testable.

### BankAliasRegistry (309 lines)
38 `BankDefinition(code, fullName, headerPrefixes, bodyKeywords)` entries (BOB→NSDL + Fi/Jupiter/Slice/Niyo). `identifyBankName`: header prefix substring match FIRST (list order matters!), then body keyword substring match. Gotcha: matching is plain `contains`, e.g. prefix "FI" would match any header containing "FI". `matches(accountBankCode, parsedBankName, senderHeader?)`: direct equality → definition lookup (by code/fullName/headerPrefix equality) → parsed-name-contains-prefix / header-contains-prefix → bidirectional substring fallback. Pure JVM.

### SmsReceiver.onReceive (222 lines)
Multi-PDU concat → parse → `goAsync()` + ad-hoc `CoroutineScope(Dispatchers.IO)`:
1. Linked-account gate L61–77: first account where `BankAliasRegistry.matches(bankCode, bankName, sender)` OR both last4 non-null equal; fallback single-account; if none matched and no bank matches any account → silent drop.
2. Dedup L79–85: MD5 of `"$sender-${amount}-${type}-${rawBody.take(30)}"` into SharedPreferences **"sms_processed_hashes"**; comment claims 7-day window but NO TTL/pruning exists anywhere — hashes persist forever.
3. Balance sync L87–136: if endingBalance && matchedAccount.lastKnownBalance>0: expected = prev ∓ amount; discrepancy ≥ ₹1.0 ⇒ insert missed-transaction PendingTransactionEntity (merchant "Missed Transaction (Balance Sync)", timestamp now−1000) + `postMissedTransactionNotification`; always updates account.lastKnownBalance/lastBalanceTimestamp.
4. Manual reconciliation L138–155: `findMatchingManualTransaction(amount, INCOME|EXPENSE, yesterday)`; suppress (record hash, return) when targetAcc==null || manual.account==targetAcc.name.
5. Recon hash L157–162: key `"recon_${amount}_${type}"` (written by NotificationActionReceiver after inward-transfer log) → consume + suppress.
6. Auto-merge L164–215: `findMatchingOppositePending(amount, oppositeType, now−120s)` with different bankName ⇒ insert TRANSFER tx (first TRANSFER category else id 1L; notes "Auto-merged inter-account transfer (...)"), delete opposite pending, performBackup(), `postAutoMergeSuccessNotification` (Undo, setTimeoutAfter 5000ms).
7. Else: store hash → insert pending → `postBankTransactionNotification` (guarded POST_NOTIFICATIONS check API<33 || granted).
Catch-all prints stack trace; finally `pendingResult.finish()`. MD5 helper duplicated here and in SmsCatchUpSync.

### SmsCatchUpSync.runSync(context) (154 lines) — called from MainActivity LaunchedEffect
READ_SMS guard; queries `content://sms/inbox` last 24h DESC; per message: parse → same linked-account gate → Tier1 dual hash check (hash variants with timestamp AND body[0..30]) → Tier2 30-minute window match against existing pendings (±30min same amount/type) or logged transactions (±30min, type mapped CREDIT→INCOME/DEBIT→EXPENSE, TRANSFER counts as match) → manual-reconciliation (same as receiver) → Tier3 balance verification: computes per-account net from last-3-days txs; |net − endingBalance| < 1.0 ⇒ mark seen & skip → else insert pending (NO notification) + store hashes. Whole body wrapped in catch-log-all.

### SmsNotificationHelper (627 lines)
Channel `bank_transactions_channel` IMPORTANCE_HIGH. Notification id scheme `(pending.id * 1000L).toInt() and 0x7FFFFFFF`. Actions (string constants L22–31): ACTION_LOG_TRANSACTION, ACTION_SWAP_TRANSFER_ACCOUNTS, ACTION_SWAP_CREDIT_TRANSFER, ACTION_LOG_INWARD_TRANSFER, ACTION_PAGINATE_NOTIFICATION, ACTION_DISMISS_NOTIFICATION, ACTION_UNDO_AUTO_MERGE, ACTION_SELECT_EXPENSE_CATEGORY, ACTION_SELECT_INCOME_CATEGORY. Extras constants L33–43.

- Stage 1 `postBankTransactionNotification` (L70–198): DEBIT → [💸 Expense][⇄ Transfer][❌ Dismiss]; CREDIT → [💰 Income][⇄ Transfer In][❌ Dismiss]; PRIORITY_MAX; content tap opens MainActivity.
- Stage 2 `postCategoryPickerNotification` (L208–291): pages of 2 categories of requested type (modulo total), buttons labeled `"$emoji $name"`, [🔄 More...] paginates via ACTION_PAGINATE_NOTIFICATION.
- `swapToCreditTransferAccountsNotification` (L300–368): source-account picker excluding accounts whose bankCode equals credited bank (equals ignoreCase against FULL NAME — bankCodes are short codes like "BOB" so this filter rarely excludes anything; quirk).
- `postAutoMergeSuccessNotification` (L377): id `(txId xor currentTimeMillis)`; timeoutAfter 5000; [↩️ Undo].
- `postSuccessNotification` (L412): timeoutAfter 3000.
- `postMissedTransactionNotification` (L427–466): BigTextStyle.
- `getCategoryEmoji(iconName)` (L475–564): blank→"📋"; any char code >127 / surrogate / OTHER_SYMBOL → return trimmed input AS-IS (emojis pass through); else legacy Material-name→emoji when-table (~60 entries); unknown → `iconName.take(2)`. Pure logic despite Android-heavy file.

### NotificationActionReceiver (346 lines)
Routes every action via goAsync + IO scope. Key behaviors:
- SELECT_*_CATEGORY → stage 2 page 0. PAGINATE → mode switch ("TRANSFER_CREDIT"→account picker; "EXPENSE"/"INCOME"→category picker page N; else legacy stage 1).
- DISMISS (swipe deleteIntent) → cancel notification ONLY; pending row intentionally kept.
- UNDO_AUTO_MERGE → delete tx by EXTRA_TX_ID if exists, performBackup, success toast-notification.
- LOG_INWARD_TRANSFER (L209–267): double-tap guard `getById ?: return` THEN delete pending; target = account.bankCode.equals(targetBankName /* FULL bank name */) ?: primary ?: first (short-code bankCodes make first match unlikely — falls back to primary; quirk); first TRANSFER category else id 1L; writes `"recon_${amount}_CREDIT"` hash; performBackup; success notification.
- LOG_TRANSACTION (L273–344): same guard; category name+type match ?: first-of-type ?: id 1L; account bankCode.equals(bankName) ?: primary ?: first ?: "Primary Bank"; notes "Auto-logged from SMS"; TRANSFER toAccount default "Savings"; performBackup; success notification.

## 5. Repositories (data/repository/)

- TransactionRepository (254L): thin DAO wrapper; EVERY insert/update/delete triggers `autoBackupManager?.performBackup()`. Analytics helpers: getMonthlyTrend (N sequential per-month queries), getWeekdayAverages (divides by distinct weekdays capped at LocalDate.now()), getCategoryTrend (top-N chosen from END MONTH only; map keyed by categoryId.toString()). `repairCorruptedTransferCategories()` swallows exceptions.
- AccountRepository (122L): rename overload propagates via transactionDao.updateAccountName + updateToAccountName; setPrimaryAccount loops N updates (no transaction); `syncAccountNames()` (startup): rewrites stale transaction account names via hardcoded legacy mapping primary←{"Primary Bank","Cash","primary"}, 2nd←{"Secondary Bank","secondary"}, 3rd←{"Savings Bank","savings"}.
- BackupRepository (122L): `exportReport(uri, format, targetMonth?)` → fresh ReportGenerator; `smartImport` → AutoBackupManager.smartImportFromDocuments; `importBackup(uri)`: content starting "{" → restoreFromJson (success count = TOTAL tx count post-import, not rows added), else CSV import path builds missing categories with hardcoded Material name `"MoreHoriz"`/`#607D8B`.
- SpendingLimitRepository (68L): DataStore file **"hisab_limit_settings"**, keys limit_type/limit_amount/limit_date/limit_enabled; enum LimitType DAILY/WEEKLY/MONTHLY/SPECIFIC_DAY; data class SpendingLimitConfig(type=DAILY, amount=1000.0, specificDate=today, isEnabled=false); clearLimit only flips enabled=false.
- CategoryRepository (45L), PendingTransactionRepository (27L — does NOT trigger backups; AutoBackupManager snapshots pendings directly).

## 6. Backup system (data/backup/)

AutoBackupManager (549L). `BACKUP_VERSION=5`, `MAIN_BACKUP_NAME="hisab_auto_backup.json"`.
- `performBackup()`: full 4-table serialize → ALWAYS writes internal `filesDir/backups/hisab_auto_backup.json` → best-effort copies to cartesian product {Public Documents, Public Downloads, /storage/emulated/0/Documents, /storage/emulated/0/Download, app-external Documents, app-external root} × {"Hisab", ""} (≤12 files, per-copy empty catches) → MediaStore upsert under Documents/Hisab/ → updateLastBackupTime(). Runs after EVERY mutation (write amplification).
- Restore scan order: smartImportFromDocuments scans Documents/Hisab → Documents → Downloads/Hisab → Downloads → hardcoded /storage/emulated/0/* and /sdcard/* paths → app-external dirs; accepts ANY *.json (or exact name) containing literal `"transactions"`; then MediaStore query DATE_MODIFIED DESC. `restoreIfEmpty()` prefers INTERNAL copy first, only when transactions table empty.
- `restoreFromJson`: requires "transactions" array; optional accounts/categories/pendingTransactions; account match by name→same-type→insert (bankCode/last4 preserved when import null); category dedupe `${name}_${type}` (missing → MoreHoriz/#607D8B defaults); tx fingerprint includes createdAt → identical logical rows re-import duplicate; category fallback chain map-miss→first-of-type→id 1L; NOT atomic. JSON checksum SHA-256 computed but NEVER verified; BACKUP_VERSION never checked.
- BackupPreferences: DataStore **"hisab_auto_backup_prefs"**, auto_backup_enabled (default TRUE), last_backup_time.

## 7. Export/Import (data/export/ReportGenerator.kt, 525L)

ExportFormat enum: PDF/XLSX/CSV/JSON (displayNames verbatim). `generateReport(uri, format, transactions, categories, targetMonth?)`: filters by month when targetMonth!=null; sorts ASC oldest-first; returns count.
- PDF: A4 PdfDocument canvas, 24 ledger rows/page, KPI cards (Income #10B981, Expense #EF4444, Savings=#8B5CF6 sum of TRANSFER, Net #3B82F6), dates `dd MMM yyyy`, chart page always appended (donut top-8 ALL types combined; daily line uses `transactions.first().date.lengthOfMonth()` else 30 — wrong span for all-records exports; bars fixed buckets 1–6/7–12/13–18/19–24/25–31). Cosmetic bug: footer "Page $n of $n".
- XLSX (POI XSSFWorkbook): sheet "Ledger & Transactions", 9 headers Date/Type/Category/Account/To Account/Debit (Expense)/Credit (Income)/Savings/Notes; numeric amounts with 0.0 placeholders.
- CSV: hand-rolled 9-column writer, naive escaping (does NOT double embedded quotes — corrupts on quotes/newlines). **CRITICAL: incompatible with importer** — CsvHelper.csvToTransactions expects legacy 6 columns `Date,Type,Category,Amount,Account,Notes` (amount at index 3); importing an app-exported CSV silently drops every row.
- JSON: IGNORES targetMonth and the passed transactions — delegates to AutoBackupManager.exportBackupString() (full DB dump).

CsvHelper (108L): RFC-style escaping writer; reader state-machine cannot handle doubled `""` or multiline quoted values; malformed rows silently skipped; account default "Cash".

## 8. Models & utils (pure JVM unless noted)

- TransactionType {INCOME, EXPENSE, TRANSFER}; MonthlySummary(totalIncome,totalExpense,netBalance,transactionCount); DailyTotal(date,totalAmount); CategoryBreakdown(categoryId,categoryName,colorHex,iconName,totalAmount,percentage,transactionCount).
- CurrencyFormatter: en-IN grouping `#,##,##0.00`; negative uses Unicode MINUS U+2212 (`−₹…`) — beware string matching; formatCompact Cr/L/K thresholds 10M/100K/1K (Locale.US decimals).
- DateUtils: English-only formatters; formatRelative Today/Yesterday literals (PDF export deliberately avoids them); daysElapsed: current→today.dayOfMonth, past→full length, future→0.
- CategoryIconMapper (200L, Compose-dependent): `getIcon(iconName)` maps MATERIAL names→ImageVector, else Icons.Filled.MoreHoriz — **DEAD CODE, never called** (verified grep: only `getAccountIcon` used, in AccountsOverviewWidget L77 + SettingsScreen L566/L1267). getAccountIcon: cash→Payments, card|credit→CreditCard, upi|wallet|pay→AccountBalanceWallet, else AccountBalance. Unused imports of this class remain in ExpenseLeaderboard/TopCategorySpendWidget/TransactionItem.

Icon rendering truth: UI displays emojis via `SmsNotificationHelper.getCategoryEmoji(category.iconName)` (SettingsScreen tiles, CategoryPicker, ExpenseLeaderboard, TopCategorySpendWidget, TransactionItem, CategoryEditDialog). DB stores emojis (post 6→7 migration; CategoryEditDialog offers 50 preset emojis + free-text input); legacy Material names still resolve via the mapping table. Both systems coexist by design.

## 9. ViewModels & state pattern

Uniform: MutableStateFlow + flatMapLatest/combine + `stateIn(SharingStarted.WhileSubscribed(5000))`; UI collects via collectAsState(); no LiveData anywhere.
- DashboardViewModel (403L): init repairs corrupted transfer categories; flows listed L65–254 incl. accountBalances (TRANSFER moves account→toAccount), savings detection by name contains "saving" ignoreCase; spendingLimitStatus warning ≥75%, exceeded >100%; approvePendingTransaction picks category by name+type else first-of-type else id 1L, account primary else first else "Primary Bank", notes "Auto-logged from SMS".
- HistoryViewModel (159L): server-side date filter, in-memory type/account/search filtering (search matches category/notes/raw amount/formatted currency/accounts).
- AnalyticsViewModel (482L): loads ALL transactions 2020-01-01..2030-12-31 into memory; enums CategoryFilterType{ALL,INCOME,EXPENSE,TRANSFERS} (default EXPENSE), BarChartTimeFilter{TODAY,SPECIFIC_DATE,WEEKLY,FIFTEEN_DAYS,MONTHLY} (default MONTHLY); savings account = name contains "Savings" OR type=="SAVINGS"; public mutable selectedSecondaryAccountName/barChartFilter/barChartSpecificDate StateFlows.
- SettingsViewModel (215L): DataStore "hisab_settings" declared here; availableExportMonths grouped from all transactions desc; export success msg "Exported $count records ($scopeText) as ${format.displayName} successfully".

## 10. Screens/components/charts (key facts)

- SettingsScreen (1658L): sections = Accounts Management, collapsible Income/Expense/Transfer category accordions (grid tiles of 4, emoji via getCategoryEmoji, colorHex via android.graphics.Color.parseColor try/catch), Data & Backup card (auto-backup Switch reading BackupPreferences directly, Export Report → ExportFormatDialog [All Records vs Specific Month radio + per-month txn counts], Import Backup → smartImport with SAF OpenDocument fallback mime array ["application/json","text/csv","text/comma-separated-values","*/*"], App Info & Restricted Settings), About card with HARDCODED "Hisab v3.1.2"/"Build 312" strings (drift vs versionName 3.2.0/320). SAF launchers CreateDocument/OpenDocument; filenames `Hisab_Statement_${MMM yyyy}.ext` or `Hisab_Statement_All_Records_${yyyy-MM-dd}.ext`. Toasts for export/import results; SnackbarHost present but unused.
- BankSelectionSheet (284L): module-level `ALL_INDIAN_BANKS` (46 entries incl. PYTM/AIRTEL/JIO payment banks); Save button requests RECEIVE_SMS+READ_SMS(+POST_NOTIFICATIONS on 33+) via RequestMultiplePermissions BEFORE saving mapping. The ONLY runtime permission request in the entire UI layer.
- QuickAddSheet (507L): keypad constraints (single dot, ≤2 decimals); TRANSFER requires ≥2 distinct accounts; date picker ↔ LocalDate via systemDefault zone; nested AddAccountDialog drops bankCode/last4.
- DashboardScreen: end-of-month banner when dayOfMonth >= lengthOfMonth−1; recent = first 10 of month flow; fallback category literals "#607D8B"/"MoreHoriz".
- Components with hardcoded colors: income 0xFF00E676, expense 0xFFFF5252, transfer 0xFF64B5F6 recur across FilterBar/QuickActionsRow/TransactionItem/HistoryScreen/AnalyticsScreen.
- Charts are ALL custom Canvas (Vico dependency exists but charts don't use it): DonutChart (ModernDonutPalette 12 colors, 18° gaps, MoM chip), AnalyticsLineChart (mode EXPENSE/INCOME/BOTH), UnifiedBarChart (imports analytics ViewModel enums — chart↔VM coupling), SpendingHeatmap (Monday-first, min-max normalization over nonzero days), WeekdayBarChart (dashed average line). UNUSED charts: CategoryTrendChart, DailyLineChart, IncomeExpenseBarChart, WeekdayBarChart (no screen references found).
- Unused components: SummaryCard, StoragePermissionDialog IS used (MainActivity), AutoRestoreLoadingDialog IS used (MainActivity); NumericKeypad.onClear param never invoked internally.

## 11. Theme

Theme.kt: `LocalHisabColors` staticCompositionLocalOf with HisabExtendedColors (income/expense/warning surfaces, chartColors 8, heatmap ramps dark+light); accessor `HisabTheme.colors`. Color.kt palette: PrimaryTeal 0xFF00E5A0, backgrounds DarkBackground 0xFF0F0F14/LightBackground 0xFFF8F9FC. Type.kt: downloadable Google Fonts "Outfit"+"Inter" via Play Services provider (font_certs.xml) — the only network-fetched asset family (silently falls back offline).

## 12. Tests

app/src/test: SmsBankParserTest (167L, 11 tests): real user-reported BOB SMS fixtures (₹40/₹45/₹30), alias matching for 7 banks, word-boundary cases ("Dear"/"card"/"drive" must not trigger), JIOFIBER rejection, JIOPB acceptance, ATM withdrawal, getCategoryEmoji direct+legacy mapping. NOTE: it instantiates SmsNotificationHelper (Android-importing file) but only calls its pure getCategoryEmoji function — safe under JVM. ExampleUnitTest trivial. androidTest: ExampleInstrumentedTest only.

## 13. Master gotcha list (all verified)

1. Room: destructive fallback + hand-written migrations — new entity changes MUST add migration (see AGENTS.md).
2. Every tx/account/category mutation = FULL backup rewrite to ≤13 locations (write amplification; empty catches hide failures).
3. Two incompatible CSV dialects: exported 9-col CSV cannot be re-imported (legacy 6-col only). JSON import ignores filters entirely.
4. sms_processed_hashes SharedPreferences never expire (comment claims 7 days) — reinstall preserves dedup state only until app data clear.
5. NotificationActionReceiver matches accounts via `bankCode.equals(fullBankName)` — short codes never equal full names; falls back to primary/first silently.
6. Backup JSON checksum/version written but never validated; restore not atomic.
7. Hardcoded fallback triple ["Primary Bank","Secondary Bank","Savings"] recurs in 3 ViewModels + DB seed; savings detection = substring "saving".
8. Version strings hardcoded in SettingsScreen ("Hisab v3.1.2"/"Build 312"); versionName is 3.2.0/320; README claims v3.1.2.
9. Orphaned features: budgets + recurring rules (tables/DAOs only); dead code: CategoryIconMapper.getIcon, SummaryCard, 4 chart composables.
10. CurrencyFormatter negative sign U+2212 breaks naive string comparisons/search.
11. AnalyticsViewModel holds all transactions (2020–2030) in memory.
12. No INTERNET permission (offline-first enforced); only explicit intents: GitHub profile link, App Info settings, MANAGE_APP_ALL_FILES_ACCESS_PERMISSION.
