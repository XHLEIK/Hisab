<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v4.0</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (v4.0 Backup & Permission Overhaul)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.0.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v4.0-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v4.0">
      <img src="https://img.shields.io/badge/GitHub-v4.0_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v4.0 Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.0.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v4.0.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v4.0.apk` (and `Hisab_v3.1.2.apk`, `Hisab_v3.1.1.apk`, `Hisab_v3.1.apk`, `Hisab_v3.0.apk`)
- **File Location**: [`releases/Hisab_v4.0.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.0.apk)
- **Latest Tag**: [`v4.0`](https://github.com/XHLEIK/Hisab/releases/tag/v4.0)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v9**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, real-time instant bank SMS transaction detection, multi-factor deduplication, category-aggregated expense leaderboards, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features & Changelog (v4.0 Update)

### 1. 🗂️ Single-File Backup Discipline
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
