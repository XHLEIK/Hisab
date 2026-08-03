# Hisab (हिसाब) v2.1.0 - Smart Offline-First Personal Finance Tracker

**Hisab** is a modern, privacy-focused, offline-first personal expense and finance tracker built for Android using **Jetpack Compose**, **Room Database**, **Material 3**, and **Custom Canvas Data Visualizations**.

Designed with a sleek, minimalist dark UI (inspired by Revolut and Apple Wallet), Hisab gives users complete control over their money with multi-account tracking, intelligent spending limits, comprehensive charts, and professional financial export capabilities—all while keeping data 100% private and stored locally on device.

---

## ✨ Features (v2.1.0)

### 1. 📊 Master Hero Card & Financial Hub
- **Unified Master Hero Card**: Centered Month/Year selector (`< August 2026 >`), status badge (`On Track` / `Deficit`), and `Net Balance` (`Primary + Secondary Accounts`).
- **3-Column Micro Metrics**: Income (`+₹15,287`), Expenses (`-₹7,840`), and Savings (`₹2,408`) separated by clean vertical dividers.
- **Sleek Quick Actions**: Instant access pills for `Income`, `Expense`, and `Transfer`.
- **Accounts Overview**: Cool gradient cards for Primary Bank, Secondary Account, and Savings Account.
- **Top Expense Categories**: Full-width category progress rows sitting directly on screen background.

### 2. 💸 Multi-Account & Transfer Management
- **Transaction Types**: Support for **Expense**, **Income**, and **Transfer** entries.
- **Transfer Categories**: Categorize transfers into *Savings*, *Investment*, *Stocks*, *Fixed Deposit*, *Mutual Funds*, and *Other Transfer*.
- **Quick Entry Keypad**: Custom numeric keypad for ultra-fast transaction logging.
- **Material Vector Icons**: Clean, emoji-free vector icon system across all categories and accounts.

### 3. 📈 Modern Analytics & Visualizations
- **Donut Chart**: Rounded-cap arcs with 18-degree wide gaps, 12-tone subtle pastel palette, center total readout, and status badge (`+2.5% ↑`).
- **Income, Expense & Transfers Bar Chart**: Granular trend analysis with weekly navigation controls (`← / →`) and fixed half-month views.
- **Daily Trends Line Chart**: Interactive canvas line chart supporting Expense line, Income line, and Both (Overlay) modes.
- **Top Expenses Leaderboard**: Ranked leaderboard of highest monthly expenses.
- **Spending Heatmap**: Calendar view highlighting daily spending intensity.

### 4. ⚡ Spending Limits & Budget Pace
- **Monthly Budget Progress Card**: Live spending limit tracking with spent vs limit text readout and progress bar.
- **Safe Daily Pace**: Real-time calculation of daily allowable spend based on remaining monthly budget.
- **Visual Budget Health**: Status indicators (*On Track*, *Warning*, *Exceeded*).

### 5. 📄 Executive Reports & Data Export
- **PDF Balance Sheet**: Multi-page A4 executive financial statement featuring canvas-drawn 30-day whole-month line & bar charts, summary KPI cards, category breakdown tables, and chronological transaction ledgers.
- **Excel Workbook (.xlsx)**: Styled multi-sheet workbooks generated using Apache POI with headers, cell borders, currency formatting, and auto-sized columns.
- **CSV & JSON**: Plain-text spreadsheet exports and raw data backup files.

### 6. 🛡️ Auto-Backup & Scoped Storage Restoration
- **Background Auto-Backup**: Automatically saves encrypted/checksummed JSON backups to `Documents/Hisab/hisab_auto_backup.json` after every transaction mutation.
- **Scoped Storage & MediaStore Integration**: Robust discovery using Android MediaStore API for automatic background restoration upon app reinstall.
- **100% De-duplication Engine**: Transaction fingerprinting prevents any duplicate entries during restoration.

### 7. ⚙️ Settings, Licenses & Privacy
- **App Build**: `v2.1.0 (Build 210)`
- **Open Source Licenses**: MIT License, Jetpack Compose, Room, Kotlin Coroutines, Material 3.
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

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17 / Kotlin 2.0+
- Android SDK Platform 36

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/XHLEIK/Hisab.git
   cd Hisab
   ```
2. Open the project in Android Studio.
3. Sync Gradle project files.
4. Run on an Android Emulator or physical device:
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
│   │   │   └── repository/     # Repositories (Transaction, Category, Account, Limit)
│   │   ├── ui/
│   │   │   ├── charts/          # Canvas Charts (Line, Bar, Donut, Heatmap)
│   │   │   ├── components/      # Reusable Compose UI widgets & dialogs
│   │   │   ├── navigation/      # NavHost & Screen routes
│   │   │   ├── screens/         # Dashboard, Analytics, History, Settings screens
│   │   │   └── theme/           # Hisab Custom Dark Theme & Design Tokens
│   │   └── util/                # Icon mappers, Formatters, DateUtils
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

---

## 🔒 Privacy & Security

Hisab is built with privacy as a foundational principle:
- **Zero Cloud Tracking**: All transactions, accounts, and budgets stay 100% offline on your device.
- **Local Storage**: Storage Access Framework (SAF) and MediaStore APIs ensure data is written to user-controlled directories (`Documents/Hisab/`).
- **Data Integrity**: SHA-256 checksum validation guarantees backup file authenticity.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **MIT License**.
