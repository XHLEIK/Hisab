<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v3.0</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (Final Publishing Release)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-3.0.0.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v3.0.0-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v3.0.0">
      <img src="https://img.shields.io/badge/GitHub-v3.0.0_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v3.0.0 Final Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-3.0.0.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v--3.0.0.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v-3.0.0.apk`
- **File Location**: [`releases/Hisab_v-3.0.0.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-3.0.0.apk)
- **Latest Tag**: [`v3.0.0`](https://github.com/XHLEIK/Hisab/releases/tag/v3.0.0)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v5**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, automated bank SMS transaction detection, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features (v3.0 Final Publishing Update)

### 1. 🤖 Automated Bank SMS Transaction Engine
- **Multi-Stage SMS Parser**: Real-time parsing of incoming bank transaction SMS using TRAI DLT header validation (`-T` Transactional / `-S` Service). Automatically filters out promotional (`-P`), government (`-G`), and failed/declined/reversed transaction noise.
- **Dynamic 3-Button Notification Category Pagination**:
  - Respects Android's 3-action button OS limit while offering unlimited category browsing.
  - **Debit (Payment)**: `[ 🛒 Groceries ]`, `[ 🛍️ Shopping ]`, `[ 🔄 More... ]`.
  - **Credit (Income)**: `[ 💼 Salary ]`, `[ ⇄ Transfer In ]`, `[ 🔄 More Income... ]`.
  - Tapping `[ 🔄 More... ]` updates the notification shade **in-place** via deterministic notification IDs without opening the app or creating duplicate notification cards.
- **Credit Inward Transfer Source Picker**:
  - Tapping `[ ⇄ Transfer In ]` on a credit alert transforms the notification layout in-place to list source accounts (`[ 🏦 Secondary Bank ]`, `[ 🐷 Savings ]`, `[ 🔄 More Accounts... ]`).
  - Automatically logs a single `TRANSFER` entry (`Savings → Primary Bank`), synchronizing both account balances.
- **120-Second Inter-Account Transfer Auto-Merging**:
  - Automatically correlates DEBIT and CREDIT SMS alerts from distinct accounts received within 120 seconds into a single `TRANSFER` transaction (`Primary Bank → Savings`).
  - Displays a 3-second auto-dismissing toast: `"✓ Auto-Detected Transfer: Primary Bank → Savings (₹500)"` with an `[ Undo ]` action button.
- **24-Hour Delayed SMS Catch-Up Sync (`SmsCatchUpSync.kt`)**:
  - Background scanning of device SMS inbox for the last 24 hours on app launch (safely guarded with `READ_SMS` permission checks).
  - Reconciles manual entries automatically and adds unlogged bank alerts directly to the **Pending Transactions Card** on the Dashboard for 1-tap review.

### 2. 🏦 Multi-Account & Searchable 46+ Bank Linking
- **Searchable Bank Selector**: Link 46+ Indian banks (SBI, Bank of Baroda, HDFC, ICICI, Axis, PNB, Canara, Kotak, Union, Federal, AU, Payments Banks, Neo-banks, etc.) and account last 4 digits.
- **Dynamic Unlink Action in Settings**:
  - Linked accounts display green status badges (`Linked: Bank (A/C **1234)`).
  - Tapping a linked account presents an **"Unlink Bank Account"** action (`LinkOff` icon in red) that cleanly unlinks the bank mapping and triggers auto-backup.

### 3. 🎨 Searchable 40+ Category Icon Library
- **40+ Material Icons**: Includes T-Shirt/Apparel (`Checkroom`), Washing Machine (`LocalLaundryService`), Coffee (`Coffee`), Fastfood (`Fastfood`), Pets (`Pets`), Fuel (`LocalGasStation`), Bus (`DirectionsBus`), Bike (`TwoWheeler`), Repairs (`Build`), Medicine (`MedicalServices`), Gaming (`SportsEsports`), Headphones, TV, WiFi, Electricity, Water, Salon, etc.
- **Real-Time Icon Search Bar**: Instantly filter category icons in the Add/Edit Category dialog by typing keywords like `"shirt"`, `"wash"`, `"coffee"`, `"fuel"`, `"game"`, `"pet"`.

### 4. 💰 Accurate Savings Movement & Hero Card Metrics
- **Net Monthly Savings Calculation**: The Master Hero Card Savings metric dynamically factors both incoming savings (transfers/income into Savings) and outgoing savings (transfers out of Savings or direct expenses paid from Savings), ensuring 100% 1:1 balance synchronization with Accounts Overview.

### 5. ⚡ 60 FPS Performance & Floating Glass Dock
- **Floating Glassmorphic Navigation Bar**: Ultra-smooth bottom dock with 88% surface opacity, 16.dp backdrop blur, and crisp active indicators.
- **Full Edge-to-Edge Display Height**: Layouts utilize 100% of display height across all device aspect ratios with zero artificial top gaps.

### 6. 📊 Master Financial Hub & Analytics
- **Hero Card**: Adaptive container featuring Month/Year selector (`< August 2026 >`), status badge (`On Track` / `Deficit`), and `Net Balance` (`Primary + Secondary`).
- **3-Column Micro Metrics**: Income (`+₹15,602`), Expenses (`-₹7,851`), and Savings (`₹2,558`) separated by clean vertical dividers.
- **Donut Chart**: Rounded-cap arcs, 18-degree wide gaps, 12-tone subtle pastel palette, center total readout, and status badge (`+2.5% ↑`).
- **Unified Spending Trends Card**: 3-tab switcher supporting Line Trend, Monthly Bar Chart, and Calendar Heatmap.

### 7. 🎯 Spending Limits & Budget Pace
- **Monthly Budget Progress Card**: Live spending limit tracking with spent vs limit readout and progress bar.
- **Safe Daily Pace**: Real-time daily allowable spend calculation guarded with division-by-zero bounds.

### 8. 📄 Executive Reports & Data Export
- **PDF Balance Sheet**: Multi-page A4 executive financial statement featuring canvas-drawn 30-day whole-month line & bar charts, summary KPI cards, category breakdown tables, and multi-page chronological transaction ledgers.
- **Excel Workbook (.xlsx)**: Styled multi-sheet workbooks generated using Apache POI.

### 9. 🛡️ 100% Complete Auto-Backup & Restore Engine (v5 Schema)
- **Automatic Background Backup**: Encrypted/checksummed JSON backups saved to `Documents/Hisab/hisab_auto_backup.json` after every transaction mutation.
- **100% Schema Coverage**: Preserves accounts, categories, transactions, budgets, recurring rules, bank mappings (`bankCode`, `accountLast4`), pending transactions queue, and `createdAt` timestamps for 100% duplicate protection on restore.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Declarative UI with Material 3)
- **Database**: Room Database v5 (SQLite ORM with versioned migrations `MIGRATION_3_4`, `MIGRATION_4_5`)
- **State Management**: Kotlin Coroutines, StateFlow, ViewModel, Clean Architecture
- **Export Engines**: Apache POI (`poi-ooxml`), Android `PdfDocument`, Gson
- **Navigation**: Jetpack Navigation Compose
- **Target SDK**: Android 16 (API 36), Min SDK 28 (Android 9.0+)

---

## 🚀 Getting Started

### Direct APK Download
Click the **Download APK** button above or download [`releases/Hisab_v-3.0.0.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-3.0.0.apk) directly to your Android device.

### Build & Run from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/XHLEIK/Hisab.git
   cd Hisab
   ```
2. Open the project in Android Studio (Ladybug | 2024.2.1 or newer).
3. Sync Gradle project files.
4. Build the debug or release APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔒 Privacy & Security

Hisab is built with privacy as a foundational principle:
- **Zero Network Tracking**: All transactions, SMS messages, accounts, and budgets stay 100% offline on your device.
- **Local SMS Processing**: Bank SMS parsing happens entirely on your phone. Zero data ever leaves your device.
- **Data Integrity**: SHA-256 checksum validation guarantees backup file authenticity.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **Apache License 2.0**.
