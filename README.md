<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="Hisab Logo" />
  <h1 align="center">Hisab (हिसाब) v3.1.2</h1>
  <p align="center"><b>Smart Offline-First Personal Finance Tracker for Android (v3.1.2 Bank AI & 2-Stage Notifications Release)</b></p>
  <p align="center">
    <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.2.apk">
      <img src="https://img.shields.io/badge/📥_Download_APK-v3.1.2-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
    </a>
    <a href="https://github.com/XHLEIK/Hisab/releases/tag/v3.1.2">
      <img src="https://img.shields.io/badge/GitHub-v3.1.2_Release-blue?style=for-the-badge&logo=github" alt="GitHub Release" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge" alt="License" />
    </a>
  </p>
</p>

---

## 📲 Click to Download APK (v3.1.2 Release)

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.2.apk">
    <img src="https://img.shields.io/badge/⚡_DIRECT_DOWNLOAD-Hisab__v3.1.2.apk-00E5A0?style=for-the-badge&logo=android&logoColor=black" alt="Direct APK Download" />
  </a>
</p>

- **APK File**: `Hisab_v3.1.2.apk` (and `Hisab_v3.1.1.apk`, `Hisab_v3.1.apk`, `Hisab_v3.0.apk`)
- **File Location**: [`releases/Hisab_v3.1.2.apk`](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v3.1.2.apk)
- **Latest Tag**: [`v3.1.2`](https://github.com/XHLEIK/Hisab/releases/tag/v3.1.2)

---

## 📌 Overview

**Hisab** is a state-of-the-art, 100% offline-first personal finance tracker built for Android using **Jetpack Compose**, **Room Database v6**, **Material 3**, and **Custom Canvas Graphics**.

Designed with a sleek, adaptive fintech UI supporting both **Light Mode** and **Dark Mode**, Hisab gives users total financial clarity with multi-account management, real-time instant bank SMS transaction detection, multi-factor deduplication, category-aggregated expense leaderboards, dynamic notification category pagination, inter-account auto-merging, 24-hour delayed SMS catch-up sync, 40+ searchable category icons, and executive PDF/Excel financial statement exports—all while keeping your financial data strictly 100% private on your local device.

---

## ✨ Key Features & Patch Notes (v3.1.2 Update)

### 1. 🧠 Multi-Pass Credit/Debit Direction Engine
- **Strict Word-Boundary Regex (`\b`)**: Evaluates action verbs (`credited`, `received`, `deposited`, `debited`, `spent`, `withdrawn`, `paid`, `refund`, etc.) using strict word boundaries to prevent accidental matches on unrelated words (*"Dear"*, *"Drive"*, *"Card"*).
- **Proximity-Based Scoring**: Weighs keywords based on character proximity to the monetary amount with inverse-distance decay.
- **Credit-First Precedence & Tie-Breaking**: Guarantees refunds and small credits (e.g. ₹30) are accurately recognized as credits rather than debits.

### 2. 🛡️ Linked-Account Whitelisting Gate & DLT Blacklist
- **DLT Service Header Blacklist**: Automatically discards promotional and non-bank service SMS (`JIOFIBER`, `AIRTELFI`, `SWIGGY`, `ZOMATO`, `AMAZON`, `FLIPKART`, `IRCTC`, etc.) at Stage 0.
- **Account Whitelist Verification**: Incoming bank alerts are matched against the user's active linked accounts (`bankCode` / `accountLast4`); unlinked messages are ignored silently.
- **Payment Bank Support**: Dedicated support for payment banks (`JIOPB`, `ARTLPB`, `PYTMPB`, `IPPB`, `NSDL`).

### 3. 🔔 2-Stage Stateful Notification Pipeline with Dynamic Emoji
- **Stage 1 (Intent Selection)**: Clean prompt offering `[ 💸 Expense ]` / `[ 💰 Income ]`, `[ ⇄ Transfer ]`, and `[ ❌ Dismiss ]`.
- **Stage 2 (Category Picker)**: In-place category selection with custom per-category emojis (`☕ Coffee`, `🍽️ Dining Out`, `🚗 Transport`, `🧺 Laundry`, `👕 Apparel`, `🏥 Hospital`, `🐷 Savings`, `💼 Work`).

### 4. 📊 Relative Min-Max Heatmap Normalization
- **Percentile Intensity Scaling**: Heatmap dynamically calculates $\frac{\text{amount} - \text{minAmount}}{\text{maxAmount} - \text{minAmount}}$ across non-zero spending days, ensuring rich color contrast across all daily spending levels.

### 5. 🔄 Account-Aware Pre-Notification Reconciliation
- **$\pm 1.0$ Rounding Tolerance**: Automatically reconciles bank SMS with pre-existing manual entries within $\pm ₹1.0$ tolerance.
- **Account Alignment Guard**: Verifies that the manual entry belongs to the same bank account as the SMS sender, preventing false suppressions across multiple accounts.

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
