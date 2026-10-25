# KinTime

A native Android app for a family to understand screen time and daily movement together. Supports **Android 15–17** (minimum API 35, compile/target API 37).

## Download the Android app

**[Download KinTime for Android (APK)](https://github.com/itssrokay/KinTime/releases/latest/download/KinTime-android.apk)** · [Releases and release notes](https://github.com/itssrokay/KinTime/releases) · [SHA-256 checksum](https://github.com/itssrokay/KinTime/releases/latest/download/SHA256SUMS)

Latest version: **0.3.0**. This is a development build signed with the Android debug certificate. Download the APK on an Android 15+ phone and allow installation from your browser if prompted. Automatic screen-time tracking has its own setup in **Tracking**. Family sharing still needs Firebase configuration below. Steps can be used locally without Firebase.

## What works in this build

- **Steps:** Health Connect daily totals, an editable step goal, seven-day history, source setup guidance, and separate step-sharing consent. Supports data shared by Samsung Health and Fitbit/Google Health, plus built-in phone steps on supported Android system versions.
- **Today:** daily phone time, app breakdowns, a seven-day date selector, a personal goal, and optional short-video estimates.
- **Tracking:** Usage Access setup, explicit accessibility disclosure, profile/goal settings, Samsung troubleshooting, local-history deletion.
- **Family:** Email/Password accounts, create/join a family, expiring invitation codes, live daily snapshots, optional app breakdowns, pause sharing, and leave/sign out with removal of shared statistics.
- Room database, offline local collection, periodic WorkManager jobs, retry-safe per-device daily cloud snapshots, and Firestore access rules.

The app runs without Firebase configuration. It shows a setup explanation in the Family tab, rather than sample people or fabricated statistics. **Cross-phone sharing requires the one-time setup in [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).**

Actual emulator screenshots: [Steps](docs/screenshots/steps.png), [Today](docs/screenshots/today-tracking.png), [app breakdown](docs/screenshots/app-breakdown.png), [Family setup](docs/screenshots/family-setup.png), [Tracking](docs/screenshots/tracking.png). See [verification results](docs/VERIFICATION.md).

## Enable steps

Open **Steps → Connect step data**, accept the disclosure, and allow Health Connect **read Steps** access. In Samsung Health or Fitbit/Google Health, separately enable writing Steps into Health Connect and sync the watch/tracker. Supported updated Android phones can also count steps through Health Connect itself. Optional background access allows periodic refresh; otherwise open KinTime to update.

For family steps, enable both **Share my daily totals** and **Share steps with family** in Family. The **Steps leaderboard** shows one latest reading per person, avoiding duplicate totals across their devices. Local steps do not require Firebase; family sharing requires deploying the updated Firestore rules.

Read [the source research and full setup guide](docs/STEPS_INTEGRATION.md), with links to Android, Samsung, and Google documentation.

## Build and install

1. Open this folder in Android Studio with JDK 21 (JDK 17+ is required).
2. Install Android SDK Platform 37.0 and Build Tools 36.0.0. Android Studio can create `local.properties` for your SDK location.
3. Run:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The pinned Gradle wrapper downloads Gradle 9.3.1 and verifies its checksum. The project uses AGP 9.1.1, Kotlin/Compose compiler 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.4, WorkManager 2.11.0, and Firebase BOM 35.0.2. Debug APKs are for household testing; use your own release signing key for durable distribution and future updates.

## On each phone

1. Open **Tracking**, set the person's name, and choose a goal.
2. Enable **Usage Access** for KinTime. Return to the app; refresh collects recent system events.
3. Optionally enable **Reels & Shorts estimates**, read/accept the separate disclosure, then enable **KinTime · short-video estimates** in Android Accessibility Settings.
4. Sideloaded apps may require App info → three-dot menu → **Allow restricted settings**. Samsung's Auto Blocker can also affect installation. Only change these settings deliberately for your own trusted build.
5. On Samsung, consider adding KinTime to Battery → Background usage limits → **Never sleeping apps** if updates are delayed.
6. Once Firebase is configured, create individual accounts, create/join a family, and explicitly enable **Share my daily totals**. App breakdown sharing is a separate choice.

## Measurement definitions and limits

- Device time is the union of **interactive, unlocked** intervals. It is not simply the sum of app times.
- App time is the union of resumed activities within each package while the phone is interactive/unlocked. Split-screen apps may overlap. Public events identify activity classes; multiple instances of the same class are not independently identifiable here.
- The collector reprocesses today and the preceding two days, with a preceding-day lookback for session state. Android retains detailed events only for a few days. Missing/pruned intervals cannot be recovered reliably. Blank is unknown, not zero. Totals may differ from Digital Wellbeing.
- Short-video estimates require a recognised native Instagram/YouTube viewer and a usable accessible content identity. An appearance lasting at least **two seconds** counts once. Video loops do not count again; revisits can. Ads and clips can be difficult to distinguish. Browser feeds, Instagram Lite, work profiles, and Secure Folder are outside this version.
- The detector checks only supported apps, does no screenshot recording or gesture injection, and keeps UI identities in memory. Persisted short-video samples contain package, counts, durations, time, and detector version. App UI changes, sensitive-node restrictions, service loss, or process death can cause undercounts or overcounts. **Real-device detector accuracy is not established by an emulator smoke test.**
- Collection and upload are periodic, with no exact timing guarantee. A service/process gap can lose short-video events. `updatedAt` reflects upload time; collection time is stored separately.
- Per-device screen-time and short-video statistics are added together per account. Step totals use one latest reading per person/day instead. Concurrent devices can therefore exceed an individual's elapsed wall-clock time. Family snapshots use each device's local date; mixed-time-zone families should interpret dates carefully.

## Privacy and cloud behavior

Local Room storage is app-private and excluded from Android backup. Sharing defaults off. Firebase uses in-memory Firestore caching; raw UI text and screenshots are not uploaded. App/package breakdowns are opt-in. Each family member can read the family's published summaries and write only their own. Invitation codes are 128-bit random, expire after 24 hours, and cannot be listed. An inviter must still be a member when the code is redeemed. Members may invite others; treat codes as access credentials.

Pausing sharing, leaving, and signing out delete that account's published summaries after an online request completes. Local history is retained. An offline/failed deletion shows an error and must be retried. This removes records across that account's devices; a different still-sharing device can subsequently publish again. Previously viewed/exported information cannot be recalled. Deleting local history alone does not delete cloud data, and recent system screen-time events and connected Health Connect totals may be reimported. Step-only opt-outs save failed cleanup requests and retry on the next sync, even with sharing paused.

This MVP retains local daily records until deleted. Active step syncing removes this device’s family step snapshots outside its latest seven available days. It has no account-deletion UI, administrator console, invitation-management history, or automatic expiry/purge of old group metadata. Those are needed before broader distribution. Firebase client identifiers are not admin credentials; **access protection depends on deploying and testing the included rules**. Never put a service-account key in the app. Public Google Play distribution requires a separate policy assessment for accessibility and adult-monitoring functionality.

## Verification

```sh
# Pure calculation/state-machine tests + Android lint + APK
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# On an emulator or connected Android 15+ device
./gradlew :app:connectedDebugAndroidTest

# Firebase rules tests, isolated from production (no Firebase account needed)
# Use Node 24.12+ (see .nvmrc).
npm ci
npm run test:rules

# With a running Android emulator: two real SDK clients against local Auth/Firestore
npm run test:sync
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md) for design details, evidence, and the household trial checklist.
