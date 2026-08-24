# Implementation Plan: Comprehensive Bug Fixes & Architectural Enhancements (v3.2.0)

This plan provides a deeply researched, foolproof technical blueprint to resolve all 5 issues documented in [issues.txt](file:///c:/Users/ASUS/Desktop/Hisab/issues.txt) with zero regressions.

---

## 1. Root Cause Analysis & Resolution Architecture

### Issue 1: SMS Notifications Inconsistent Triggering (BOB ₹40 worked, ₹45 & ₹30 failed)
#### Deep Root Cause Breakdown:
1. **Bank Identification & Alias Matching Failure in Linked-Account Gate**:
   - In [BankSelectionSheet.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/BankSelectionSheet.kt), `bankCode` is saved as `"BOB"`.
   - In [SmsBankParser.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsBankParser.kt), `BANK_SENDER_MAP` maps `"BOB"` $\rightarrow$ `"Bank of Baroda"`.
   - In [SmsReceiver.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsReceiver.kt) line 64:
     `parsed.bankName.uppercase().contains(account.bankCode!!.uppercase())` evaluated `"BANK OF BARODA".contains("BOB")` which is **FALSE**!
   - For BOB ₹40 debit: The SMS contained `A/C XXXXXX1463`, which matched `account.accountLast4 == "1463"`, so it passed through the last-4 backdoor.
   - For BOB ₹30 credit (`"Dear BOB UPI User: Your account is credited with INR 30.00... AvlBal: Rs840.43 - BOB"`):
     The SMS has NO account number (`accountLast4 = null`). Because `"BOB"` $\neq$ `"Bank of Baroda"` and last-4 was null, **the SMS was silently discarded!**
2. **Payee VPA vs Sender Bank Confusion in `identifyBankName()`**:
   - For BOB ₹45 debit (`"...Cr. to yespay.bizsbiz102249@yesbankltd... AvlBal:Rs810.43... Call ...-BOB"`):
     Scanning the body matched `"YES"` / `"yesbankltd"` first if sender header was non-standard. The parsed bank was identified as `"YES Bank"` instead of `"Bank of Baroda"`, causing linked account check to fail.
3. **Catch-Up Sync Race Condition**:
   - If [SmsCatchUpSync.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsCatchUpSync.kt) ran when opening the app around the time of the transaction, it inserted into pending transactions without firing a notification and saved the message hash, causing [SmsReceiver.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsReceiver.kt) to skip notification.

#### Solution Architecture:
- Create a canonical **BankAliasRegistry** that bridges short codes (`"BOB"`), full names (`"Bank of Baroda"`), and DLT header prefixes (`"BOBTXN"`, `"BOBSMS"`, etc.).
- Update `identifyBankName()` to prioritize sender headers first, and for body parsing, strictly look at bank sign-offs / closing statements (e.g. `"- BOB"`, `"-BOB"`) over beneficiary VPA strings.
- Fix linked account verification in both `SmsReceiver` and `SmsCatchUpSync` to use `BankAliasRegistry.matches(account.bankCode, parsed.bankName)`.

---

### Issue 2: Missed Transaction Detection via Ending Balance Sync
#### Deep Architecture:
- Real-world scenario: An SMS for a ₹10 transaction is missed by the telecom operator. The next transaction of ₹10 arrives with `AvlBal: Rs 790.43`, but the app's previous known balance was `Rs 810.43`.
- Expected balance after current ₹10 debit = `810.43 - 10.00 = 800.43`.
- Actual balance in SMS = `790.43`.
- Discrepancy = `800.43 - 790.43 = 10.00` (Missed Debit!).
- Implementation:
  1. Add `lastKnownBalance: Double?` and `lastBalanceTimestamp: Long?` to [AccountEntity.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/db/entity/AccountEntity.kt) (Room Migration).
  2. When `parsed.endingBalance` is present:
     - Compare against expected balance from previous `lastKnownBalance`.
     - If discrepancy $\ge 1.00$:
       - Create an automated `PendingTransactionEntity` with note `"Auto-Detected Missed Transaction (Balance Discrepancy)"` and merchant `"Unlogged Transaction"`.
       - Fire a dedicated notification: `"⚠️ Missed Transaction Detected: ₹10.00"`.
     - Update the account's `lastKnownBalance` to the current `parsed.endingBalance`.

---

### Issue 3: Google Play Protect Remediation
#### Deep Root Cause Breakdown:
1. **Excessive & Dangerous Permissions**:
   - `android.permission.MANAGE_EXTERNAL_STORAGE` in [AndroidManifest.xml](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/AndroidManifest.xml) is a high-risk restricted permission meant only for system file managers. Combining this with `RECEIVE_SMS` and `READ_SMS` triggers severe Play Protect heuristics on sideloaded APKs.
   - `READ_MEDIA_VIDEO` and `READ_MEDIA_AUDIO` are declared but never used.
2. **Unsigned Release Builds / Debug Key Heuristic**:
   - [app/build.gradle.kts](file:///c:/Users/ASUS/Desktop/Hisab/app/build.gradle.kts) had no signing configuration for release builds. Debug builds signed with the generic Android SDK key trigger Play Protect warnings.

#### Solution Architecture:
- Remove `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` from [AndroidManifest.xml](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/AndroidManifest.xml).
- Maintain scoped storage via Android Storage Access Framework (`CreateDocument` contract) which requires zero extra storage permissions.
- Configure clean signing configs and ProGuard rules in [app/build.gradle.kts](file:///c:/Users/ASUS/Desktop/Hisab/app/build.gradle.kts).

---

### Issue 4: Category Emoji System (Replacing Icon Library with Native Emojis)
#### Deep Root Cause Breakdown:
- The app stores Material icon names (e.g. `"ShoppingCart"`, `"Restaurant"`) in `CategoryEntity.iconName`.
- Notifications use a separate hardcoded lookup in `SmsNotificationHelper.getCategoryEmoji()`, causing new user categories or unmapped categories to fallback to a generic clipboard emoji `"📋"`.
- The Material icon search picker in `CategoryEditDialog` takes excessive space and is limited compared to Android's native emoji keyboard.

#### Solution Architecture:
1. **Data Model & Migration**:
   - Update default categories in [HisabDatabase.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/db/HisabDatabase.kt) to store Unicode emojis directly (e.g. `"🛒"`, `"🍽️"`, `"🛍️"`, `"🚗"`, `"🧾"`, `"💰"`, `"💼"`, `"🏦"`, etc.).
   - Implement a backward-compatible converter: If an existing category has a legacy icon name like `"ShoppingCart"`, automatically resolve it to its corresponding emoji `"🛒"`.
2. **UI Modernization**:
   - Update [CategoryEditDialog.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/CategoryEditDialog.kt):
     - Remove the icon search bar and the Material icon grid.
     - Add an interactive **Emoji Selector**: Displays a prominent circular emoji badge. Tapping opens an emoji text field allowing full selection from the native Android keyboard, alongside a curated quick-pick emoji palette.
   - Update [CategoryPicker.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/CategoryPicker.kt), [DashboardScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/dashboard/DashboardScreen.kt), [HistoryScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/history/HistoryScreen.kt), [SettingsScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsScreen.kt), [ExpenseLeaderboard.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/ExpenseLeaderboard.kt), and [TopCategorySpendWidget.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/TopCategorySpendWidget.kt) to render emojis cleanly.
3. **Notification Streamlining**:
   - In [SmsNotificationHelper.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsNotificationHelper.kt), use `category.iconName` directly as the button emoji with zero hardcoded lookup tables.

---

### Issue 5: Month-Filtered Financial Export Reports
#### Deep Root Cause Breakdown:
- [SettingsScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsScreen.kt) currently only allows exporting all historical transactions at once.
- Users cannot export a specific month's statement (e.g. August 2026).

#### Solution Architecture:
1. **Export Dialog Redesign**:
   - Update `ExportFormatDialog` in [SettingsScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsScreen.kt):
     - **Scope Selection**:
       - 🔘 **All Records (All-Time Statement)**
       - 🔘 **Specific Month**: Dynamically displays a dropdown / selectable list of **only** the months that contain at least one transaction (e.g. `"August 2026 (18 transactions)"`, `"September 2026 (4 transactions)"`).
     - **Format Selection**: PDF Report, Excel (.xlsx), CSV, JSON Backup.
2. **Backend & Report Generator Updates**:
   - Update [BackupRepository.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/repository/BackupRepository.kt) and [ReportGenerator.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/export/ReportGenerator.kt):
     - Accept an optional `targetMonth: java.time.YearMonth?`.
     - Filter transactions when `targetMonth` is specified.
     - Dynamic filename generation: `Hisab_Statement_Aug_2026.pdf` vs `Hisab_Statement_All_Records.pdf`.
     - Tailor PDF title, subtitle, date periods, and charts to the specific month range.

---

## 2. Proposed Changes by Component

### Component A: SMS & Parser Engine
- **[NEW]** [BankAliasRegistry.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/BankAliasRegistry.kt): Centralized bank code, name, and alias matching table.
- **[MODIFY]** [SmsBankParser.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsBankParser.kt): Enhance sender header parsing priority, fix BOB UPI credit parsing without account number, ignore beneficiary VPAs during sender bank identification.
- **[MODIFY]** [SmsReceiver.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsReceiver.kt): Integrate `BankAliasRegistry`, implement missed transaction balance reconciliation engine.
- **[MODIFY]** [SmsCatchUpSync.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsCatchUpSync.kt): Use `BankAliasRegistry`, avoid suppressing real-time notification hashes.
- **[MODIFY]** [SmsNotificationHelper.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/sms/SmsNotificationHelper.kt): Direct emoji binding, add missed transaction alert notification builder.

### Component B: Security & Play Protect
- **[MODIFY]** [AndroidManifest.xml](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/AndroidManifest.xml): Remove `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`.
- **[MODIFY]** [build.gradle.kts](file:///c:/Users/ASUS/Desktop/Hisab/app/build.gradle.kts): Configure release signing configuration with v1/v2/v3 signatures.

### Component C: Category System & Emojis
- **[MODIFY]** [HisabDatabase.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/db/HisabDatabase.kt): Update default category entities to use Unicode emojis; Room migration 6 $\rightarrow$ 7 for account balance tracking.
- **[MODIFY]** [AccountEntity.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/db/entity/AccountEntity.kt): Add `lastKnownBalance: Double?` and `lastBalanceTimestamp: Long?`.
- **[MODIFY]** [CategoryEditDialog.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/CategoryEditDialog.kt): Replace icon search with native emoji picker & quick palette.
- **[MODIFY]** [CategoryPicker.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/CategoryPicker.kt), [DashboardScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/dashboard/DashboardScreen.kt), [HistoryScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/history/HistoryScreen.kt), [SettingsScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsScreen.kt), [ExpenseLeaderboard.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/ExpenseLeaderboard.kt), [TopCategorySpendWidget.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/components/TopCategorySpendWidget.kt): Render emojis directly.

### Component D: Export Reports
- **[MODIFY]** [ReportGenerator.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/export/ReportGenerator.kt): Support month-filtered exports and dynamic titles.
- **[MODIFY]** [BackupRepository.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/data/repository/BackupRepository.kt): Add `targetMonth` parameter.
- **[MODIFY]** [SettingsViewModel.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsViewModel.kt): Provide available months with transaction counts.
- **[MODIFY]** [SettingsScreen.kt](file:///c:/Users/ASUS/Desktop/Hisab/app/src/main/java/com/example/hisab/ui/screens/settings/SettingsScreen.kt): Redesign `ExportFormatDialog` with All Records vs Specific Month selection.

---

## 3. Verification Plan

### Automated Unit Tests
- Run `./gradlew testDebugUnitTest` to verify:
  1. `SmsBankParserTest`: Parse the exact BOB ₹40 debit, ₹45 debit, and ₹30 credit messages.
  2. `BankAliasRegistryTest`: Verify matching of `"BOB"` with `"Bank of Baroda"`, `"AD-BOBTXN"`, `"BOBSMS"`.
  3. `ReportGeneratorTest`: Verify month filtering and ledger generation.

### Build & Compilation
- Run `./gradlew assembleDebug` and `./gradlew assembleRelease` to ensure clean builds and correct APK artifact generation.
