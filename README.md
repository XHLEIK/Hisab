<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v4.1</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (v4.1 Full-Screen Transaction Composer)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.1.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v4.1-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v4.1">
      <img src="https://img.shields.io/badge/GitHub-v4.1_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v4.1 Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.1.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v4.1.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v4.1.apk` (and `Hisab_v4.0.apk`, `Hisab_v3.1.2.apk`, `Hisab_v3.1.1.apk`, `Hisab_v3.1.apk`, `Hisab_v3.0.apk`)
- **File Location**: [`releases/Hisab_v4.1.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.1.apk)
- **Latest Tag**: [`v4.1`](https://github.com/XHLEIK/Hisab/releases/tag/v4.1)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v9**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, real-time instant bank SMS transaction detection, multi-factor deduplication, category-aggregated expense leaderboards, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features & Changelog (v4.1 Update)

### 🆕 v4.1 — Full-Screen Premium Transaction Composer
- **Full-screen Add/Edit experience**: `Add Entry` / `Edit Entry` now open as a true full-screen composer (`Dialog` + `Scaffold`) — `Dashboard`/`History` no longer bleeds around edges. Subtle drag handle (`40×4dp`) at top + natural swipe-down dismiss (finger-tracking `Animatable` + `draggable` `160dp`/`800 velocity` threshold, `tween 220/260`) replaces the redundant header `X`/`✓` — bottom `Save`/`Update` is the sole commit action. Gesture matches `Category Picker` (`ModalBottomSheet`) and is confined to the header so calculator/category/account/date interactions never accidentally dismiss.
- **One unified glass bottom dock**: Calculator + Account + Date/Notes + Save live on a single `Haze` (`HazeState`/`hazeChild` `0.82/14dp` `0.5dp` border `24dp` top) translucent dock (`shadow 8dp`, `surfaceContainerHigh 0.82`) — no competing sticky `calculator + giant Account panel` layers. `Scaffold bottomBar` `navigationBarsPadding+imePadding` keeps it above keyboard/nav bar.
- **Persistent calculator (compact & premium)**: `NumericKeypad` reworked to `5×4` (`% ÷ × −` / `7 8 9 +` / `4 5 6 ⌫` / `1 2 3 =` tall `=` 98dp / `00 0 .`) `46dp` keys `6dp` gaps `12dp` radius — dense but comfortable. Pure-Kotlin `CalculatorEngine` (`BigDecimal`, shunting-yard, standard `×÷%` → `+-` precedence, contextual `%`: `50%→0.5`, `1000×10%→100`, `1000+10%→1100`, `÷0→null`, `00`/`Backspace`/`=` only, no `C`).
- **Single-page hierarchy (no hidden content)**: `Header → Type → Amount (₹ displayMedium, no heavy card) → [Split] → Category → Calculator → Account/From-To → Date/Notes → Save`. Main page is now `fillMaxSize` non-scrollable with type-specific responsive gaps (`Expense 14/10/8/16/14` vs `Income+Split 6/4/4/8/8` vs `Transfer 8/6/6/12/10`) — `Category` card (`40dp→36dp` when `Income+Split`) always finishes `16dp+` above calculator top, no overlap/clipping on small screens, no nested scroll jitter (`LazyVerticalGrid` → static `Column(chunked 4)` for picker grid, outer `Column` handles scroll only in picker).
- **Compact category selector + dedicated picker**: Main screen shows only selected category — `Box(14dp, border 0.5dp)` `40dp Circle` emoji + `titleSmall` name + `type` label + `ChevronRight` (`+ Select a category` placeholder when `selectedCategoryId==0`). Tap opens dedicated `ModalBottomSheet` (`20dp` top, dragHandle) — `Search` (`contains ignoreCase` immediate) + `Recent (4)` `LazyRow` (`CategoryStripItem 96-156dp 44dp`) + `All Categories` `4-col` `CategoryGridPickerItem(44dp 18sp, 2-lines)` scrollable `560dp` `rememberScrollState` — scales `6→50` categories, no horizontal carousel swipe fatigue, `Select a category` shown until user picks.
- **Income + Split + Transfer polish**: `Split Reimbursement` compact `12→8dp` padding when `Income+Split`; `Reimbursed Category` label; `Expense` reference layout untouched; `Transfer` `FROM`/`TO` `labelSmall Bold 0.8sp` + `1dp divider 0.35` + `AccountPicker` (no large green arrow), `ACCOUNT` header `AccountBalanceWallet 16dp + ACCOUNT 0.8sp + N accounts` + `horizontalScroll` `Spacer 8+2dp` so `+ Add Account` never clipped.
- **Bug fixes**: `888` now correctly shows `888` (was only first digit — `amountText` vs `calculator.expression` `State` sync fixed), `−` unicode vs `-` and `×÷` handling normalized, `10+20×2=50` precedence fixed.
- **Patch 2025-08-30 — Real-device responsiveness (v4.1 patch, no version bump)**: Fixed `Category` being hidden/clipped behind the calculator dock on physical phones (emulator was fine). Root cause was fixed vertical assumptions — now uses `LocalConfiguration.screenHeightDp` (`isShortScreen <700dp`, `isVeryShort <620dp`) + `Scaffold innerPadding` (actual `WindowInsets` + measured `bottomBar`) to reserve the dock height dynamically. `Expense` remains reference; `Income + Split` and `Transfer` now use type + height responsive gaps (`topGap 14→6→4dp`, `amountVert 10→6→4dp`, `contentVertPad 16→8→6dp`, `Split 10→8→6dp`, `Category card 14→10→8dp` + icon `40→36→32dp`, `dock 12→10→8dp`) so `Category` always finishes `16dp+` above `Calculator` with no overlap/clipping, no `verticalScroll` reintroduced, no `horizontal carousel`, no green arrow, no tiny fonts. Verified on tall/standard/short emulators + physical device.

### v4.0 — Backup & Permission Overhaul (previous)
#### 1. 🗂️ Single-File Backup Discipline
- **Eliminated duplicate backup files**: The app now enforces exactly ONE backup file (`hisab_auto_backup.json`) in `Documents/Hisab/`. Previous versions could create deconflicted copies like `hisab_auto_backup(1).json` due to POSIX and MediaStore write races.
- **Smart deconfliction cleanup**: On startup and after every backup, stray `(N)` variant files are cleaned up from Documents, Downloads, and MediaStore.
- **Best-candidate import**: When restoring, the app now searches all `hisab_auto_backup*.json` variants (including deconflicted copies) and picks the one with the most transactions — so old backup files are never silently ignored.

### 2. 🔄 Live Backup on Every Change
- **Automatic backup after every data mutation**: Backups are triggered after every transaction insert/update/delete, category change, account change, and pending transaction approval — ensuring the backup file is always up to date.

### 3. 🔐 All Files Access Permission Flow (Android 11+)
- **Proper MANAGE_EXTERNAL_STORAGE prompting**: On Android 11+ (API 30+), the app now correctly prompts for All Files Access permission, which is required to read/write backup files in `Documents/Hisab/` via the File API.
- **Sequential permission chain**: Storage permission now appears in the correct order (after notification, before SMS) on all Android versions, including Android 13+ where legacy storage permissions are deprecated.

### 4. 📱 SMS Permission Flow with Restricted Settings Guide
- **Android system dialog first**: The app fires the system SMS permission dialog first — this is required on MIUI/HyperOS devices to make the "⋮ → Allow restricted settings" menu appear in App Info.
- **Guided restricted settings flow**: If SMS permission is blocked by restricted settings, the app shows a step-by-step instruction dialog with an "Open App Info" button. After the user enables restricted settings and returns, the SMS permission card is automatically re-shown for a retry.
- **Retry card for non-restricted denial**: If SMS permission is simply denied (not restricted), the app shows a card with an "Allow" retry button instead of silently dropping the flow.

### 5. 📄 Improved PDF Reports
- **Account Balances section no longer overlaps**: Fixed the "Account Balances (Current)" header text overlapping with KPI cards in the PDF export. The section now has proper spacing and renders cleanly above the balance cards.

### 6. 🏠 Hero Card Savings Accuracy
- **Deducts all savings outflows**: The dashboard hero card's Savings tile now correctly calculates net savings flow using `SplitAccounting.accountBalance()` — matching the Accounts Overview section. Previously, expenses charged to the savings account were not deducted from the savings display.

### 7. 🎨 Analytics Heatmap with Relative Color Grading
- **True relative intensity**: The daily expense heatmap now uses continuous color interpolation based on `amount / maxAmount`, so the highest expense day is dark red and the lowest is light green, with smooth gradient shades in between.
- **Eliminated uniform colors**: Previously, all days with expenses appeared similar because the intensity calculation used min-max normalization across non-zero days only. Now every day gets a unique shade proportional to its spending.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Declarative UI with Material 3)
- **Database**: Room Database v9 (SQLite ORM with migrations through v9)
- **Target SDK**: Android 16 (API 36), Min SDK 28 (Android 9.0+)

---

## 🔒 Privacy & Security

- **Zero Network Tracking**: All transactions, SMS messages, accounts, and budgets stay 100% offline on your device.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **Apache License 2.0**.
