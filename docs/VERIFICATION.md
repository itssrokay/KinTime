# Verification — 25 October 2026

## Version 0.2.0: activity logging

- Android debug APK builds successfully; Android lint reports zero errors.
- **16 JVM tests passed**, including 6 new tests for activity duration formatting, midnight/DST splitting, range totals, and invalid/future manual entries.
- **5 Android tests passed** on Android 17: the two existing onboarding/disclosure tests, two new activity-log UI tests (manual entry, timer start/stop and activity recreation), and a non-destructive database migration test. The migration test preserves existing usage rows and checks that only one running timer is allowed.
- Schema 1 → 2 adds `time_entries` without deleting existing screen-time tables. Exported schemas for both versions are included.
- The two-account Authentication/Firestore emulator sync integration test also passed with the version 2 database.
- Captured the new timer and grouped-history screens after manually starting/stopping an example activity on the Android 17 emulator.
- The APK is still a development build; actual family devices, Samsung background behavior, and Instagram/YouTube recognition need the household trial below.

The initial screen-time/security verification is retained below; manual activity descriptions/projects are local only and do not change the family cloud schema or rules.

## Initial screen-time and family-sharing verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`
  - Installable debug APK produced, minimum Android API 35, target/compile API 37.
  - **10 JVM tests passed:** screen/lock/shutdown intervals, split-screen and same-package interval unions, midnight clipping, empty history, dwell thresholds, duplicate events, skips/revisits, and bounded sampling gaps.
  - **Android lint: zero errors.** Remaining warnings are dependency-update suggestions and optional Kotlin convenience API recommendations.
  - **2 Compose UI tests passed** on the Android 17 / API 37.0 arm64 emulator: honest local/Firebase onboarding and separate short-video disclosure. The explicitly emulator-dependent sync test is skipped by this ordinary command.
- `npm run test:rules`, using Node 24.12.0:
  - **11 Firestore emulator tests passed:** atomic family creation, private data access, valid/missing/expired invitations, no invitation enumeration or outsider writes, membership visibility, profile ownership, summary validation/ownership, departure, and clearing optional app breakdowns.
- `npm run test:sync`, using Node 24.12.0:
  - **1 Android SDK integration test passed**, with two independent accounts against local Authentication and Firestore emulators: register, create family, issue invitation, join, publish actual Room totals, read as another member, retry without duplication, pause and delete uploaded records, leave family.
- Manually installed the final APK on the Android 17 emulator and inspected Today, Family, Tracking, and the scrolled app breakdown.
- Granted Usage Access on the development emulator and verified that collection populated Room with a nonzero daily total and three nonzero application rows (launcher, KinTime, Settings). Screenshots show actual emulator usage, not seeded sample totals. No app crash was recorded during this check.

## Still requires a household device trial

- Android 15 physical-device installation/runtime behavior.
- Samsung One UI 9 background survival, sideload settings, and battery consumption.
- Instagram/YouTube viewer recognition on the exact versions and language settings installed on family phones. Emulator UI/state-machine tests do **not** establish Reels/Shorts detection accuracy.
- Live Firebase configuration and deployed rules. No live project or production resources were created; the shipped build deliberately supports local tracking without Firebase.
- Release signing and broader distribution review. This APK is a development build signed with the local Android debug certificate.

See `DEVICE_TEST_PLAN.md` for the device trial and `FIREBASE_SETUP.md` for enabling household sync.
