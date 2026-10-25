# Verification — 25 October 2026

## Version 0.3.0 — Steps

- APK build passed: version 0.3.0, code 4, minimum API 35, target API 37. APK signature verified, using the same certificate as 0.2.1.
- Android lint passed with **zero errors** (30 existing/dependency/convenience warnings).
- **10 JVM tests passed** for screen-time calculations and short-video state transitions.
- **9 Android tests passed** on Android 17 / API 37.0: three onboarding/setup checks, three Room migration checks, and three step repository checks. The separate Firebase integration test is skipped by the ordinary connected-test command.
- Step tests cover consent and permission gates, optional background access, real zero versus absent records, source deletion, and disconnect/revoke behavior. Schema 3 → 4 retains existing usage/video data and adds daily step history; earlier migration tests remain green.
- **14 Firestore rules tests passed**, including family-only step access, owner-only writes/deletes, valid bounded fields, rejecting stale overwrites, corrections that decrease totals, and one canonical person/day snapshot across devices.
- **1 Android Firebase integration test passed** using local Auth/Firestore emulators and two accounts: screen-time sharing plus step opt-in, daily upload, retry without duplication, count correction, independent step opt-out, persisted cleanup retried with master sharing off, and family departure. No live Firebase project was created.
- Installed the final APK on the Android 17 emulator. Opened Steps, accepted the in-app disclosure, and used the real Android Health Connect permission flow. It requested read Steps only, then successfully performed aggregate reads and displayed an honest missing reading for the empty emulator health store. No sample positive step totals were inserted for the screenshot.
- Verified the optional system background-access prompt and the in-app steps privacy activity. The Android Health Connect rationale alias is registered and permission-protected. The Steps screenshot in the README is the actual emulator screen before connection.
- Confirmed real Health Connect permissions were revoked after disconnecting and ending the emulator app process (Android defers self-revocation until it is safe to end the process). The manual activity timer/log screen remains absent.

Commands: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`; `npm run test:rules`; `npm run test:sync` (Firebase scripts run under Node 24.12.0).

Samsung/Fitbit syncing, native phone sensor accuracy, physical Android 15 behavior, and live project deployment remain household-device checks; emulator tests do not establish these.

## Version 0.2.1

- Removes the manual timer and logging screen, related code, and screenshots.
- Restores the Today, Family, and Tracking navigation.
- Database schema 3 retains the original screen-time tables. Upgrade migrations cover both earlier schemas and remove the obsolete manual-entry table from schema 2.
- APK build and Android lint passed with zero lint errors.
- **10 JVM tests passed.**
- **4 Android tests passed** on Android 17: onboarding/disclosure checks (including absence of the Log tab) and database upgrades from both schema 1 and schema 2. The migrations preserve usage and short-video rows, and remove the obsolete table from schema 2.
- Verified that the removed logging classes are absent from the APK.

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
