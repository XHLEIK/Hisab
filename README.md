<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v3.1.1</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (v3.1.1 Restricted Settings & Bank Link Restoration Release)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.1.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v3.1.1-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v3.1.1">
      <img src="https://img.shields.io/badge/GitHub-v3.1.1_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v3.1.1 Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.1.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v3.1.1.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v3.1.1.apk` (and `Hisab_v3.1.apk`, `Hisab_v3.0.apk`)
- **File Location**: [`releases/Hisab_v3.1.1.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.1.apk)
- **Latest Tag**: [`v3.1.1`](https://github.com/XHLEIK/Hisab/releases/tag/v3.1.1)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v6**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, real-time instant bank SMS transaction detection, multi-factor deduplication, category-aggregated expense leaderboards, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features (v3.1.1 Update)

### 1. 🏦 Bank Link Preservation in Backup & Restore
- **100% Bank Mapping Sync**: `bankCode` and `accountLast4` fields for all accounts are serialized to JSON backups and fully restored upon import or auto-sync, preserving linked bank accounts across device transfers.

### 2. ⚙️ Restricted Settings Setup & Onboarding Dialog
- **Android 13+ Compliance**: Guided step-by-step modal for turning on *"Allow Restricted Settings"* under Android App Info to enable 1-tap real-time bank SMS transaction logging.
- **1-Tap App Info Launcher**: Includes a direct button to launch `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with zero hassle.

### 3. 🏆 Category-Aggregated Expense Leaderboard
- **Sum of All Category Expenses**: The Expense Leaderboard groups expenses by category ID and ranks them in **biggest to smallest order**.

### 4. ⚡ Real-Time Instant SMS Heads-Up Notifications
- **High-Priority Channel**: Configured `bank_transactions` channel with `NotificationManager.IMPORTANCE_HIGH` and `setDefaults(NotificationCompat.DEFAULT_ALL)`.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Declarative UI with Material 3)
- **Database**: Room Database v6 (SQLite ORM)
- **Target SDK**: Android 16 (API 36), Min SDK 28 (Android 9.0+)

---

## 🔒 Privacy & Security

- **Zero Network Tracking**: All transactions, SMS messages, accounts, and budgets stay 100% offline on your device.

---

## 📜 License & Credits

- Developed by **Subham Bose** (GitHub: [@XHLEIK](https://github.com/XHLEIK))
- Distributed under the **Apache License 2.0**.
