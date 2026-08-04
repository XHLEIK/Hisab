<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v2.2.3</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-2.2.3.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v2.2.3-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v2.2.3">
      <img src="https://img.shields.io/badge/GitHub-v2.2.3_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v2.2.3)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-2.2.3.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v--2.2.3.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v-2.2.3.apk`
- **File Location**: [`releases/Hisab_v-2.2.3.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-2.2.3.apk)
- **Latest Tag**: [`v2.2.3`](https://github.com/XHLEIK/Hisab/releases/tag/v2.2.3)

---

## 📌 Overview

**Hisab** is a modern, privacy-focused, 100% offline-first personal expense and finance tracker built for Android using **Jetpack Compose**, **Room Database**, **Material 3**, and **Custom Canvas Visualizations**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial control with multi-account tracking, intelligent spending limits, comprehensive charts, and professional financial export capabilities—all while keeping your data strictly 100% private on your local device.

---

## ✨ Features (v2.2.3)

### 1. 💰 Accurate Savings Movement Accounting
- **Net Monthly Savings Calculation**: The Master Hero Card's Savings metric dynamically tracks both incoming savings (transfers/income into Savings) and outgoing savings (transfers out of Savings or expenses paid from Savings), ensuring perfect 1:1 balance synchronization with Accounts Overview.

### 2. ⚡ 60 FPS Performance & Floating Glass Navigation
- **Floating Glassmorphic Dock**: Ultra-smooth floating bottom navigation with 88% surface opacity, 16.dp GPU-optimized backdrop blur, and crisp active indicators.
- **Scroll Padding Clearance**: Full 115.dp bottom clearance across all screens (Dashboard, Analytics, History, Settings) so no content is obscured behind the navigation bar.
- **Full Edge-to-Edge Height**: Zero artificial status bar gaps; layouts fill 100% of the display height across all devices.

### 3. 📊 Master Hero Card & Financial Hub
- **Subtle Modern Hero Card**: Adaptive single-surface container featuring centered Month/Year selector (`< August 2026 >`), status badge (`On Track` / `Deficit`), and `Net Balance` (`Primary + Secondary Accounts`).
- **3-Column Micro Metrics**: Income (`+₹15,602`), Expenses (`-₹7,851`), and Savings (`₹2,558`) separated by clean vertical dividers.
- **Sleek Quick Actions**: Instant access pills for `Income`, `Expense`, and `Transfer`.
- **Accounts Overview**: Adaptive cards for Primary Bank, Secondary Account, and Savings Account.
- **Top Expense Categories**: Full-width category progress rows with custom color indicators.

### 4. 💸 Multi-Account & Transfer Management
- **Transaction Types**: Full support for **Expense**, **Income**, and **Transfer** entries.
- **Dynamic Category Color System**: Transfer entries dynamically render their assigned category colors across Recent Transactions and History ledgers.
- **Transfer Route Display**: Clear account direction subtitles (`From Account → To Account`).
- **Quick Entry Keypad**: Custom numeric keypad for ultra-fast transaction logging.
- **Material Vector Icons**: Clean, emoji-free vector icon system across all categories and accounts.

### 5. 📈 Modern Analytics & Visualizations
- **Donut Chart**: Rounded-cap arcs with 18-degree wide gaps, 12-tone subtle pastel palette, center total readout, and status badge (`+2.5% ↑`).
- **Unified Spending Trends Card**: 3-tab switcher supporting Line Trend, Monthly Bar Chart, and Calendar Heatmap.
- **Top Expenses Leaderboard**: Ranked leaderboard of highest monthly expenses.
- **Spending Heatmap**: Calendar view highlighting daily spending intensity.

### 6. 🎯 Spending Limits & Budget Pace
- **Monthly Budget Progress Card**: Live spending limit tracking with spent vs limit text readout and progress bar.
- **Safe Daily Pace**: Real-time calculation of daily allowable spend based on remaining monthly budget using crisp Material vector icons.
- **Visual Budget Health**: Status indicators (*On Track*, *Warning*, *Exceeded*).

### 7. 📄 Executive Reports & Data Export
- **PDF Balance Sheet**: Multi-page A4 executive financial statement featuring canvas-drawn 30-day whole-month line & bar charts, summary KPI cards, category breakdown tables, and chronological transaction ledgers.
- **Excel Workbook (.xlsx)**: Styled multi-sheet workbooks generated using Apache POI with headers, cell borders, currency formatting, and auto-sized columns.
- **CSV & JSON**: Plain-text spreadsheet exports and raw data backup files.

### 8. 🛡️ Auto-Backup & Scoped Storage Restoration
- **Background Auto-Backup**: Automatically saves encrypted/checksummed JSON backups to `Documents/Hisab/hisab_auto_backup.json` after every transaction mutation.
- **Scoped Storage & MediaStore Integration**: Robust discovery using Android MediaStore API for automatic background restoration upon app reinstall.
- **100% De-duplication Engine**: Transaction fingerprinting prevents any duplicate entries during restoration.

### 9. ⚙️ Settings, Licenses & Privacy
- **App Build**: `v2.2.3 (Build 223)`
- **Open Source Licenses**: Apache License 2.0, Jetpack Compose, Room, Kotlin Coroutines, Material 3.
- **Privacy Notice & User Agreement**: 100% Offline Policy & Local Storage safety notices.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Declarative UI with Material 3)
- **Database**: Room Database 2.7+ (SQLite ORM with versioned migrations)
- **State Management**: Kotlin Coroutines, StateFlow, ViewModel, Clean Architecture
- **Export Engines**: Apache POI (`poi-ooxml`), Android `PdfDocument`, Gson
- **Navigation**: Jetpack Navigation Compose
- **Target SDK**: Android 16 (API 36), Min SDK 28 (Android 9.0+)

---

## 🚀 Getting Started

### Direct APK Download
Click the **Download APK** button above or download [`releases/Hisab_v-2.2.3.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v-2.2.3.apk) directly to your Android device.

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

## 📁 Project Structure

```
Hisab/
├── app/
│   ├── src/main/java/com/example/hisab/
│   │   ├── data/
│   │   │   ├── backup/          # AutoBackupManager & BackupPreferences
│   │   │   ├── db/              # Room Entities, DAOs, HisabDatabase (Migrations)
│   │   │   ├── export/          # PDF, XLSX, CSV, JSON Report Generators
│   │   │   ├── model/           # Data models (MonthlySummary, TransactionType)
│   │   │   └── repository/      # Repositories (Transaction, Category, Account, Limit)
│   │   ├── ui/
│   │   │   ├── charts/          # Canvas Charts (Line, Bar, Donut, Heatmap)
│   │   │   ├── components/      # Reusable Compose UI widgets & dialogs
│   │   │   ├── navigation/      # NavHost & Screen routes
│   │   │   ├── screens/         # Dashboard, Analytics, History, Settings screens
│   │   │   └── theme/           # Hisab Custom Adaptive Theme & Design Tokens
│   │   └── util/                # Icon mappers, Formatters, DateUtils
│   └── build.gradle.kts
├── docs/
│   └── logo.png
├── releases/
│   └── Hisab_v-2.2.3.apk
├── build.gradle.kts
└── README.md
```

---

## 🔒 Privacy & Security

Hisab is built with privacy as a foundational principle:
- **Zero Network Tracking**: All transactions, accounts, and budgets stay 100% offline on your device.
- **Local Storage**: Storage Access Framework (SAF) and MediaStore APIs ensure data is written to user-controlled directories (`Documents/Hisab/`).
- **Data Integrity**: SHA-256 checksum validation guarantees backup file authenticity.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **Apache License 2.0**.
