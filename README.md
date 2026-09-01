<p align="center">
  <img src="docs/logo.png" width="112" height="112" alt="Hisab Logo" />
</p>

<h1 align="center">Hisab</h1>

<p align="center">
  <strong>Private. Offline. Intelligent Personal Finance.</strong>
</p>

<p align="center">
  <em>A lightweight Android finance manager for tracking money, understanding spending, and keeping financial data on your device.</em>
</p>

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/releases/tag/v4.2.1">
    <img src="https://img.shields.io/badge/Latest-v4.2.1-111827?style=for-the-badge" alt="Latest release" />
  </a>
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.2.1.apk">
    <img src="https://img.shields.io/badge/Download-APK-16A34A?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache--2.0-2563EB?style=for-the-badge" alt="Apache 2.0" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Offline--First-Privacy--First-7C3AED?style=flat-square" alt="Offline first" />
  <img src="https://img.shields.io/badge/Android-9%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 9+" />
  <img src="https://img.shields.io/badge/SQLite%20%2B%20Room-Local%20Storage-0F766E?style=flat-square" alt="SQLite Room" />
  <img src="https://img.shields.io/badge/APK-%3C%208MB-E11D48?style=flat-square" alt="APK under 8MB" />
</p>

---

## Download

### Latest release — v4.2.1

> **APK size: under 8 MB** — designed to stay lightweight without sacrificing the app's core functionality or visual experience.

<p align="center">
  <a href="https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.2.1.apk">
    <img src="https://img.shields.io/badge/%E2%86%93%20Download%20Hisab%20v4.2.1-16A34A?style=for-the-badge&logo=android&logoColor=white" alt="Download Hisab APK" />
  </a>
</p>

- **Latest version:** `v4.2.1`
- **Target SDK:** Android 16 / API 36
- **Minimum SDK:** Android 9 / API 28
- **Release:** [GitHub v4.2.1](https://github.com/XHLEIK/Hisab/releases/tag/v4.2.1)
- **Direct APK:** [Hisab_v4.2.1.apk](https://github.com/XHLEIK/Hisab/raw/main/releases/Hisab_v4.2.1.apk)

---

## What is Hisab?

**Hisab (हिसाब)** is a privacy-first, offline-first personal finance app for Android built to make everyday money management simple, visual, and dependable.

Instead of spreading financial information across banking apps, notes, spreadsheets, and separate trackers, Hisab brings the most important workflows into one place:

- Record **income, expenses, and transfers** manually.
- Automatically detect supported bank transactions from **SMS alerts**.
- Manage **primary, secondary, savings, and additional accounts**.
- Understand spending through **charts, rankings, trends, and heatmaps**.
- Search and filter the complete **date-wise transaction history**.
- Set and monitor **daily spending limits**.
- Back up, restore, and export financial data in practical formats.

Most importantly, Hisab is designed so that your financial data remains **local to your device**. The app uses **SQLite through Android Room** for local persistence, and the project does not require a cloud account or remote finance database for its core functionality.

---

## Why Hisab?

Personal finance apps often make a simple task feel complicated: enter transactions, move money between accounts, understand spending, and keep reliable records.

Hisab focuses on one principle:

> **Your money should be easy to understand, easy to manage, and private by default.**

### What makes it different

| Capability | Hisab |
|---|:---:|
| Manual income tracking | ✅ |
| Manual expense tracking | ✅ |
| Account-to-account transfers | ✅ |
| Built-in amount calculator | ✅ |
| Split reimbursement tracking | ✅ |
| Multiple account management | ✅ |
| Bank SMS auto-logging | ✅ |
| Daily spending limits | ✅ |
| Visual analytics | ✅ |
| Expense leaderboard | ✅ |
| Spending trends | ✅ |
| Daily expense heatmap | ✅ |
| Searchable transaction history | ✅ |
| Advanced history filters | ✅ |
| Custom category management | ✅ |
| Automatic backup | ✅ |
| Backup import / restore | ✅ |
| PDF export | ✅ |
| XLSX export | ✅ |
| JSON export | ✅ |
| Light mode | ✅ |
| Dark mode | ✅ |
| Local SQLite + Room storage | ✅ |
| Lightweight APK under 8 MB | ✅ |

---

# Core Features

## 💰 Transaction Management

Hisab supports the three core transaction types in one consistent workflow:

### Expense
Record everyday spending with:

- Amount
- Category
- Account
- Date
- Notes

### Income
Track money received from sources such as salary, allowances, or other income categories.

### Transfer
Move money between your linked accounts without misclassifying internal movement as income or expense.

Transfers support clear **From → To** account handling so balances remain meaningful across accounts.

### Add, Edit & Delete
Transactions can be added, edited, and deleted while retaining the same financial model used throughout the app.

---

## 🧮 Built-in Amount Calculator

The Add Entry and Edit Entry experience includes a calculator directly inside the amount-entry workflow.

Users can calculate amounts without leaving Hisab or opening another calculator app.

Supported operations include:

- `+` addition
- `−` subtraction
- `×` multiplication
- `÷` division
- `%` percentage calculations
- Decimal amounts
- `00`
- Backspace
- `=` evaluation

For example:

```text
1000 + 250 - 50 = 1200
```

The calculated result can be used directly as the transaction amount.

The calculator is designed with financial-safe arithmetic behavior and is shared across Add and Edit Entry workflows.

---

## 🔄 Split Reimbursement

Not every incoming payment is actual income.

Hisab includes **Split Reimbursement** in the Income workflow so users can distinguish reimbursement money from genuine earnings.

For example, if you pay `₹1,000` for a group purchase and later receive `₹200` from someone sharing the cost, that `₹200` should not artificially inflate your income.

With Split Reimbursement, users can:

- Mark an incoming payment as a reimbursement.
- Associate it with the relevant expense category.
- Keep reimbursement activity separate from normal income.
- Produce more meaningful spending and income analysis.
- Reflect the practical net cost of shared expenses more accurately.

This is particularly useful for shared meals, rent, groceries, travel, games, household purchases, and other group expenses.

---

# 🏦 Multi-Account Management

Hisab is designed for people who use more than one account.

Supported account workflows include:

- **Primary account**
- **Secondary account**
- **Savings account**
- Additional linked accounts

### Account management

Users can:

- Add accounts
- Edit accounts
- Delete accounts
- View account balances
- Transfer money between accounts
- Select the correct account when recording transactions

### Account-aware transfers

Hisab treats internal account movement separately from spending and income so that moving `₹500` from Primary to Savings does not incorrectly appear as `₹500` of spending or income.

---

# 📩 Smart Bank SMS Auto-Logging

One of Hisab's most useful automation features is transaction detection from supported bank SMS alerts.

The app can use incoming bank messages to help detect:

- Debit transactions
- Credit transactions
- Transaction amounts
- The relevant linked bank/account context

The workflow is intentionally user-controlled: the detected transaction can be reviewed and classified before it becomes part of the ledger.

### Example flow

```text
Bank SMS
   ↓
Transaction detected
   ↓
Amount + debit/credit context
   ↓
Notification
   ↓
User chooses transaction type
   ↓
Category / account decision
   ↓
Transaction logged
```

The project also includes handling for missed or delayed SMS events and duplicate-prevention safeguards in the transaction pipeline.

---

# 📊 Dashboard & Financial Overview

The Home dashboard is designed to answer the most important financial questions at a glance.

### Key overview cards

- **Monthly Expense** — understand the selected month's spending.
- **Today's Expense** — see what has been spent today.
- **Savings Balance** — keep your current savings position visible.
- **Switchable Account Balance** — quickly switch between important account balances.

### Account Overview

The dashboard also provides an account-level overview for:

- Primary
- Secondary
- Savings

The app keeps period-based metrics and current account balances conceptually separate so a monthly number does not accidentally masquerade as a current balance.

---

# 🎯 Daily Spending Limits

Set a daily spending limit and use the dashboard to keep everyday spending under control.

Daily limits are useful for users who want a simple guardrail without building a complicated monthly budgeting system.

---

# 📈 Analytics

Hisab turns transaction history into visual information that is easier to understand than a long list of numbers.

## Expense Breakdown — Pie Chart

See how total spending is distributed across categories.

The chart makes it easy to identify the categories consuming the largest share of your spending.

## Expense Leaderboard

A ranked view of spending categories helps answer:

> **Where is most of my money actually going?**

This makes high-impact categories easier to spot and review.

## Spending Trends — Line Chart

Track how spending changes over time and identify periods when expenses are increasing or decreasing.

## Spending Comparison — Bar Chart

Compare spending across the app's supported time groupings to understand how one period differs from another.

## Daily Expense Heatmap

The heatmap gives each day a relative spending intensity.

- Highest-expense days receive the strongest red intensity.
- Lower-expense days use lighter shades of the same red family.
- Identical expense amounts share the same shade.
- Days with no expense remain visually empty.
- The scale recalculates when the underlying month's data changes.

This gives the user an immediate visual answer to:

> **Which days did I spend the most?**

---

# 🧾 Transaction History

The History page provides the complete date-wise financial ledger.

Users can:

- Browse transactions chronologically.
- Search for transactions.
- Apply multiple filters.
- Separate income, expenses, and transfers.
- Open individual transactions for editing.

The objective is simple:

> **Find a transaction quickly instead of scrolling through the entire month.**

---

# 🗂️ Category Management

Hisab supports independent category management for the three transaction areas.

### Expense categories

- Add
- Edit
- Delete

### Income categories

- Add
- Edit
- Delete

### Transfer categories

- Add
- Edit
- Delete

The category system is designed to remain flexible as users develop their own financial habits.

The Add/Edit Entry flow also includes a dedicated category picker with search and recent categories for faster selection.

---

# 💾 Backup, Restore & Data Portability

Financial data should not depend on a single device session.

Hisab includes backup and restore workflows designed around local data ownership.

## Automatic Backup

The app can automatically maintain its backup data after supported data changes, helping keep the latest ledger available for recovery.

## Import / Restore

Users can restore supported Hisab backup data when moving between installations or recovering their financial history.

The application includes validation and duplicate-protection measures around backup import.

---

# 📤 Export Reports & Data

Hisab supports exporting financial information into practical formats.

### PDF

Designed for readable financial statements and reports.

### XLSX

Useful for spreadsheet-based analysis and further calculations.

### JSON

Useful for data portability, structured backup, and programmatic handling.

The export system is designed to preserve meaningful transaction information rather than reducing the ledger to an unreadable raw dump.

---

# 🎨 UI/UX — Built to Feel Premium

Hisab is not designed as a spreadsheet with a mobile wrapper.

The interface is intentionally crafted to feel modern and calm while still staying information-dense enough for everyday finance management.

### Visual principles

- **Soothing visual language** in both Light and Dark modes.
- **Premium, restrained surfaces** instead of excessive visual noise.
- **Smooth page transitions** and purposeful motion.
- Responsive layouts that adapt across device sizes.
- Consistent typography and spacing.
- Clear selection states and touch targets.
- Dedicated full-screen Add/Edit transaction composition.
- Glass-inspired surfaces where they improve hierarchy without overwhelming the screen.

### Add/Edit Entry experience

The transaction composer brings the workflow together in one place:

```text
Transaction Type
      ↓
Amount / Calculator
      ↓
Split Reimbursement (Income only)
      ↓
Category
      ↓
Account / From → To
      ↓
Date + Note
      ↓
Save / Update
```

The interface is designed to remain responsive across different Android devices and screen sizes without sacrificing the visual hierarchy of the transaction form.

---

# 🔐 Privacy First & Offline by Design

Privacy is a core product principle of Hisab.

### Local financial storage

Hisab uses **SQLite through Android Room** for the application's local financial database.

The core finance data is intended to stay on the user's device rather than being uploaded to a remote personal-finance service.

### No cloud account required for core finance management

You can manage transactions, accounts, categories, budgets, and analytics locally.

### Why permissions still exist

Some features require Android permissions because Android does not allow applications to access certain device data without explicit authorization.

#### 📱 SMS permission

Required for **bank SMS auto-logging**.

Hisab needs access to relevant SMS messages so it can detect supported bank transaction alerts.

On devices where Android restricts SMS access, the user may need to follow Android's **restricted-permission / Allow restricted settings** flow before the permission can be granted.

#### 🔔 Notification permission

Required for **auto-logging notifications** so the app can notify the user when a transaction is detected and needs classification/action.

#### 📁 Storage / backup access

Required where the Android version and the configured backup workflow need access to shared backup files so Hisab can **read and write the backup file**.

The app does not need these permissions for ordinary local transaction storage inside its Room database; they are related to the specific device features that require them.

> **Permission principle:** Hisab asks for sensitive permissions because a specific feature needs them—not because the finance database itself requires cloud or broad data access.

---

# ⚡ Lightweight by Design

Hisab is intentionally optimized to keep the Android package small while retaining its full user experience.

### Current packaging goal

> **Latest release APK: under 8 MB**

The project uses Android build optimization and dependency/resource trimming to avoid shipping unnecessary code and assets.

This means you get a full personal-finance toolkit without turning the app into a massive download.

---

# 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0+ |
| UI | Jetpack Compose + Material 3 |
| Database | SQLite + Room Database v9 |
| Charts / Visuals | Custom Canvas-based visualizations where appropriate |
| Android Target | API 36 |
| Minimum Android | API 28 |
| Build Optimization | R8 + resource shrinking |

---

# 🧱 Architecture Principles

Hisab is built around a few important engineering principles:

- Local-first data ownership.
- A single authoritative financial/accounting model.
- Explicit separation between current account balances and period-based metrics.
- Deterministic transaction processing.
- Duplicate-safe automation pipelines.
- Reusable UI components rather than duplicated type-specific implementations.
- Automated tests for important accounting and transaction flows.
- Minimal dependencies and aggressive build optimization where safe.

---

# 📱 Android Requirements

| Requirement | Value |
|---|---|
| Minimum Android | Android 9 (API 28) |
| Target Android | Android 16 (API 36) |
| Architecture | Standard Android app package / release APK |
| Core database | Local SQLite via Room |
| Network required for core finance features | No |

---

# 🔑 Permissions at a Glance

| Permission / Access | Why Hisab needs it |
|---|---|
| SMS access | Detect supported bank debit/credit SMS alerts for auto-logging |
| Notification access | Show transaction auto-logging notifications |
| Backup/storage access | Read/write supported shared backup files where required by the Android version/workflow |

All permissions should be granted only when the corresponding functionality is needed.

---

# 🚀 Installation

### Download the release APK

Download the latest release from the [GitHub Releases page](https://github.com/XHLEIK/Hisab/releases).

### Build from source

Requirements:

- Android Studio
- JDK compatible with the project's Gradle/Android setup
- Android SDK with API 36 available

Then:

```bash
git clone https://github.com/XHLEIK/Hisab.git
cd Hisab
```

Open the project in Android Studio and build/run the `app` module.

For a debug build:

```bash
./gradlew assembleDebug
```

For the project's release packaging workflow, use the configured release Gradle task.

---

# 🧪 Quality & Reliability

Hisab is developed with regression testing around important financial behavior, including:

- Transaction processing
- Split reimbursement behavior
- Account balances
- Transfer handling
- Backup/restore paths
- Notification recovery
- Database migrations
- Financial summaries
- Export workflows

Build success is treated as necessary but not sufficient; important features should also be verified through their actual user flows.

---

# 📝 Version Information

## v4.2.1

**Current release**

Key focus:

- Unified Add/Edit transaction composer
- Full-screen transaction workflow
- Swipe-down dismissal
- Responsive transaction layout
- Premium calculator-style amount entry
- Compact category selection and searchable category picker
- Income Split Reimbursement workflow
- Transfer From/To refinement
- Lightweight optimized packaging

For the historical release record, see the [GitHub releases](https://github.com/XHLEIK/Hisab/releases).

---

# 📌 Patch Notes / Changelog

### v4.2.1 — Transaction Composer & Responsive UX

- Unified Add/Edit Entry into a single transaction-composer experience.
- Improved full-screen transaction flow.
- Added natural swipe-down dismissal behavior.
- Improved responsive spacing for different device sizes.
- Refined calculator-style amount entry.
- Improved category selection with search and recent categories.
- Refined Income Split Reimbursement presentation.
- Refined Transfer From/To account presentation.

### v4.1 — Full-Screen Transaction Composer

- Introduced the premium full-screen Add/Edit Entry experience.
- Added unified glass-inspired interaction areas.
- Added calculator-style amount entry.
- Added contextual percentage calculations.
- Added improved category selection.
- Refined Split Reimbursement and Transfer workflows.
- Improved physical-device responsiveness.

### v4.0 — Backup & Permission Overhaul

- Improved automatic backup handling.
- Added backup deconfliction/cleanup safeguards.
- Improved backup restoration behavior.
- Added automatic backup after supported data changes.
- Added guided SMS restricted-permission flow.
- Improved PDF reporting.
- Improved dashboard savings/account accuracy.
- Refined analytics heatmap behavior.

For older releases, use the [GitHub Releases](https://github.com/XHLEIK/Hisab/releases) history.

---

# 🗺️ Roadmap

Potential future improvements include:

- More advanced budgeting workflows.
- Additional financial insights.
- More automation sources where Android permissions and platform rules allow.
- More export/report customization.
- Further accessibility and responsive-layout refinements.

The roadmap may change as Hisab evolves.

---

# ⚠️ Known Limitations

Hisab depends on Android platform permissions and the behavior of individual banking SMS formats.

Bank SMS parsing therefore depends on supported message patterns and may require updates when banks change their notification wording.

Some Android devices also apply manufacturer-specific restrictions to SMS, background execution, notifications, or file access. Hisab provides guided permission flows where possible, but Android itself remains the final authority over device-level access.

---

# 🤝 Contributing

Contributions, bug reports, and thoughtful feature proposals are welcome.

Before submitting a change:

1. Preserve existing financial/accounting semantics.
2. Avoid unnecessary dependencies.
3. Add or update tests for behavior changes.
4. Verify both Light and Dark mode for UI changes.
5. Test on a real Android device where device-specific behavior is relevant.

---

# 🙌 Credits

**Hisab** is developed by **Subham Bose**.

- GitHub: [@XHLEIK](https://github.com/XHLEIK)
- Project: [github.com/XHLEIK/Hisab](https://github.com/XHLEIK/Hisab)

Built with the Android ecosystem and open-source tooling that makes modern local-first applications possible.

---

# 📄 License

Hisab is distributed under the **Apache License 2.0**.

See [`LICENSE`](LICENSE) for the full license text.

---

<p align="center">
  <strong>Hisab — Understand your money. Keep it yours.</strong>
</p>
