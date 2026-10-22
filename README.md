<div align="center">

<img src="tidynote.png" alt="TidyNote icon" width="128" height="128" />

# TidyNote

**An offline-first expense tracker for Indonesian small businesses — scan a receipt, and it's recorded.**

TidyNote (NotaRapi) scans shopping receipts with ML Kit on the device, parses them
with Indonesian rules (IDR, NPWP, 11% VAT/PPN), and stores them as neat expense
records — no account, no tracking, no internet required.

<br />

[![Version](https://img.shields.io/badge/version-1.0.0-006B56?style=for-the-badge)](https://github.com/s3r3/tidynote/releases)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-requirements)
[![Min SDK](https://img.shields.io/badge/minSdk-29-brightgreen?style=for-the-badge)](#-requirements)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-on--device%20OCR-FF6F00?style=flat-square&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.0-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![No Tracking](https://img.shields.io/badge/privacy-no%20tracking-success?style=flat-square&logo=shield&logoColor=white)](PRIVACY.md)

<br />

[**Features**](#-features) &nbsp;·&nbsp;
[**Promo**](#-promo) &nbsp;·&nbsp;
[**Getting Started**](#-getting-started) &nbsp;·&nbsp;
[**AI Backend**](#-backend-ai-optional) &nbsp;·&nbsp;
[**Report a Bug**](https://github.com/s3r3/tidynote/issues)

</div>

---

## 📑 Table of Contents

- [Features](#-features)
- [Promo](#-promo)
- [Tech Stack](#-tech-stack)
- [Requirements](#-requirements)
- [Getting Started](#-getting-started)
- [Project Structure](#-project-structure)
- [Backend AI (Optional)](#-backend-ai-optional)
- [Release Builds](#-release-builds)
- [Privacy](#-privacy)
- [License](#-license)

---

## ✨ Features

<table>
  <tr>
    <td width="50%" valign="top">

### 📷 Offline Scan & OCR
- ML Kit Document Scanner: automatic receipt detection & cropping
- Text Recognition runs **on-device** — no internet needed
- Or pick a receipt photo from the gallery

    </td>
    <td width="50%" valign="top">

### 🧾 Indonesian Receipt Parser
- Extracts merchant, date, line items, subtotal, discount, and total
- Understands **IDR formatting, NPWP, and 11% VAT (PPN)**
- Per-field *confidence* indicator — doubtful values are flagged for review

    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">

### ✅ Review & Edit
- Every scanned value can be edited manually before saving
- Built-in categories: Food & Drink, Ingredients, Equipment, Cleaning, Transport, Other
- Optional **Fix with AI** (opt-in, see Backend AI)

    </td>
    <td width="50%" valign="top">

### 📊 Dashboard & History
- This month's total spending, receipts needing review, latest transactions
- Spending chart per category
- Full history: search, filter, duplicate, share, detail view with the receipt photo

    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">

### 💾 Export & Backup
- Export records to **CSV** and **PDF**
- Encrypted backup (Google Tink) + restore

    </td>
    <td width="50%" valign="top">

### 🔒 Privacy
- **No accounts, no ads, no tracking**
- All data stays on the device; Android system backup is disabled for the database
- Cloud AI is off by default — see [PRIVACY.md](PRIVACY.md)

    </td>
  </tr>
</table>

---

## 🎬 Promo

<div align="center">

<video src="https://raw.githubusercontent.com/s3r3/tidynote/main/docs/promo-tidynote.mp4" controls width="720"></video>

<br />

[Watch / download the promo video](docs/promo-tidynote.mp4)

</div>

---

## 🛠 Tech Stack

| Area | Choice |
|------|--------|
| **Language** | Kotlin 2.2.20 |
| **UI** | Jetpack Compose (BOM 2024.10.01), Material 3 |
| **OCR** | ML Kit Document Scanner + Text Recognition (on-device) |
| **Persistence** | Room 2.8.5, DataStore 1.1.1 |
| **DI** | Koin 3.5.6 |
| **Images** | Coil 2.7.0 |
| **Background work** | WorkManager 2.9.1 (AI extraction retries) |
| **Backend** | Ktor 3.1.3 (`:backend` module, deployed separately) |
| **Build** | Gradle 9.8.0, AGP 8.7.3, version catalog (`gradle/libs.versions.toml`) |

> **SDK:** `minSdk` 29 · `compileSdk` / `targetSdk` 36 · JVM target 21

---

## 📋 Requirements

- JDK 21
- Android SDK 36
- Device/emulator on Android 10+ (API 29)

---

## 🚀 Getting Started

```bash
git clone https://github.com/s3r3/tidynote.git
cd tidynote
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # with a device/emulator connected
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
(the debug variant uses the `.debug` applicationId suffix, so it can live next to the release build.)

---

## 🗂 Project Structure

```
app/                          # Android app module (com.notarapi.tidynote)
  feature/
    onboarding/               # Onboarding + MainActivity
    home/                     # Home & summary
    scan/                     # Receipt scanning flow (ML Kit)
    review/                   # OCR result review & editing
    detail/                   # Receipt detail
    history/                  # History, CSV/PDF export
    dashboard/                # Statistics & category charts
    settings/                 # Settings, AI, delete data
  core/
    database/                 # Room (receipts, items, categories)
    model/ storage/ prefs/    # Models, receipt image files, preferences
    backup/                   # Encrypted backup (Tink) & CSV restore
    util/                     # Confidence colors, etc.
  data/receipt/               # Receipt repository
  extraction/
    ocr/                      # ML Kit OCR helper
    parser/                   # Indonesian receipt parser (IDR/NPWP/VAT)
backend/                      # Ktor proxy for cloud AI (optional, self-hosted)
```

---

## 🤖 Backend AI (Optional)

The app works fully offline. Cloud AI assistance only activates if you run your
own backend and enable it in **Settings → AI**:

- The backend receives OCR text (not photos) via `POST /v1/receipts/parse`
- Credentials via environment: `PROVIDER_API_KEY`, `APP_TOKENS`
- Run it: `./gradlew :backend:run` (port 8080)

Integration details: [INTEGRATION_NOTES.md](INTEGRATION_NOTES.md).
In production builds, `LLM_API_KEY` in `local.properties` **must be empty** — that
value is embedded into the APK and is meant for internal demos only.

---

## 📦 Release Builds

```bash
./gradlew :app:bundleRelease
```

Produces the AAB in `app/build/outputs/bundle/release/` (minify + resource shrinking on).

Signing is supplied via environment variables or Gradle properties (**never commit a keystore**):

- `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

Without them, the release build falls back to the debug signing config.

---

## 🔐 Privacy

TidyNote **collects no personal data and shares no data with third parties**.

- OCR runs on-device; receipt photos are stored locally in the app's storage.
- Cloud AI is **off by default**. Only if you enable it and tap *Fix with AI* is the
  OCR text (max. 8,000 characters, never the photo) sent to your own backend.
- No accounts, no ad tracking, no automatic data uploads.

Full text: [PRIVACY.md](PRIVACY.md).

---

## 📄 License

Distributed under the [Apache License 2.0](LICENSE).
Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<div align="center">

<br />

Made for Indonesian warungs & small shops 🇮🇩

⭐ **If TidyNote is useful to you, consider starring the repo!**

</div>
