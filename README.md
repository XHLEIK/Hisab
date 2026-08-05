<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v3.1</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (v3.1 Instant SMS & Category Aggregation Release)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v3.1.0-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v3.1">
      <img src="https://img.shields.io/badge/GitHub-v3.1_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v3.1 Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v3.1.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v3.1.apk` (and `Hisab_v3.0.apk`)
- **File Location**: [`releases/Hisab_v3.1.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.apk)
- **Latest Tag**: [`v3.1`](https://github.com/XHLEIK/Hisab/releases/tag/v3.1)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v5**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, real-time instant bank SMS transaction detection, multi-factor deduplication, category-aggregated expense leaderboards, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features (v3.1 Update)

### 1. 🏆 Category-Aggregated Expense Leaderboard
- **Sum of All Category Expenses**: The Expense Leaderboard sums up all expenses for each category and ranks them in **biggest to smallest order**.
- **Payment Counts**: Shows the total payment count and percentage breakdown for each category.

### 2. ⚡ Real-Time Instant SMS Heads-Up Notifications
- **High-Priority Channel**: Configured `bank_transactions` channel with `NotificationManager.IMPORTANCE_HIGH` and `setDefaults(NotificationCompat.DEFAULT_ALL)` so debit and credit notifications pop up immediately as heads-up alerts.
- **Loose Debit & Credit Regex**: Matches terms like `debited`, `dr.`, `spent`, `paid`, `transferred`, `sent to`, `vpa`, `upi` regardless of casing or extra whitespace.
- **POST_NOTIFICATIONS Guard**: Guarantees Android 13+ permission compliance before firing alerts.

### 3. 🛡️ 3-Tier Deduplication & Ending Balance Engine
- **MD5 SMS Hash Fingerprinting**: Writes unique `MD5(sender + amount + type + timestamp)` hash directly to SharedPreferences store.
- **3-Tier Catch-Up Exclusion Filter**:
  1. **Hash Check**: Discards already processed SMS hashes.
  2. **30-Minute Transaction Match**: Discards matching transactions or pending queue items within a 30-minute window.
  3. **Balance Verification**: Discards SMS alerts where extracted ending balance matches the current recorded account balance.

### 4. 🏦 Searchable 46+ Bank Selector & Unlink Bank Action
- Link or unlink 46+ Indian banks directly from Settings with green status badges and red `Unlink` action buttons.

### 5. 🎨 Searchable 40+ Category Icon Library
- 40+ Material icons (Apparel, Washing Machine, Coffee, Fastfood, Fuel, Bus, Gaming, Repairs, Pets, Salon, Medicine, etc.) with real-time keyword search.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Declarative UI with Material 3)
- **Database**: Room Database v5 (SQLite ORM)
- **State Management**: Kotlin Coroutines, StateFlow, ViewModel, Clean Architecture
- **Target SDK**: Android 16 (API 36), Min SDK 28 (Android 9.0+)

---

## 🔒 Privacy & Security

- **Zero Network Tracking**: All transactions, SMS messages, accounts, and budgets stay 100% offline on your device.
- **Local SMS Processing**: Bank SMS parsing happens entirely on your phone.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **Apache License 2.0**.
