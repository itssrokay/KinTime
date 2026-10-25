# Architecture and evidence

## Data flow

```text
Android UsageStatsManager ─→ UsageCollector ─→ UsageTimeline ─→ Room
Instagram/YouTube UI ─→ ShortVideoService ─→ VideoCounter ─────→ Room
Health Connect ───────→ StepsRepository (daily aggregate) ───→ Room
                                                               │
                            Compose ← Flow ← Room DAO           │
                                                               │ explicit sharing
                              WorkManager / app refresh ────────┘
                                          │
                       Firebase Auth + Firestore rules
                                          │
                         per-device, per-day summaries
                                          │
                          Family snapshot listeners
```

## Application components

- `core/UsageTimeline.kt`: Android-independent interval calculator, with lock, shutdown, overlap, activity-union, and day-boundary tests.
- `core/VideoCounter.kt`: Android-independent dwell/deduplication state machine.
- `tracking/UsageCollector.kt`: permission check, public event translation, bounded lookback, Room replacement transaction.
- `tracking/ShortVideoService.kt`: optional system-managed accessibility service. Bounded tree traversal, viewer/identity recognition, in-memory fingerprints, lock/foreground checks, one-second sampling, and duration splitting at local midnight.
- `steps/`: Health Connect read-only aggregate source, explicit consent/permission gates, optional background access, and Room daily step cache.
- `data/Migrations.kt`: upgrades from schema 1 or 2 to schema 3 while preserving screen-time and short-video data. The obsolete manual-entry table is removed when upgrading schema 2. Schema 3 → 4 adds step history without changing those records.
- `data/`: Room schema and observable preferences. `schemas/` contains exported database schemas for future migrations.
- `sync/FamilyRepository.kt`: optional Firebase initialization; user authentication; atomic family bootstrap; invite joining; listeners; idempotent summary upserts; deletion on pause/leave/sign-out. A mutex serialises upload with privacy deletion so a pending upload on this process cannot recreate just-deleted records.
- `sync/SyncWorker.kt`: a unique, 15-minute periodic job; local collection works offline and failed cloud requests retry.
- `ui/KinTimeRoot.kt`: Today, Steps, Family, and Tracking screens with separate accessibility and Health Connect disclosures.
- `firestore.rules`: database-enforced membership, identity ownership, server timestamps, bounded fields, invite expiry, and no invite enumeration.
- `FamilySyncTest`: two independently authenticated Android SDK clients against local Auth/Firestore emulators; exercises creating/joining a family, idempotent upload, pause cleanup, and departure. Enabled explicitly by `npm run test:sync`; ordinary UI tests skip this emulator-dependent test.

## Cloud shape

```text
families/{randomFamilyId}                  name, ownerUid, createdAt
  members/{authUid}                        name, joinedAt, inviteCode
  steps/{authUid}_{date}                  latest daily step count, device, zone, read/upload times
  summaries/{authUid}_{deviceUuid}_{date}   bounded daily totals, optional app map,
                                          source/upload timestamps, zone, detector version
invites/{128bitRandomCode}                 familyId, createdBy, createdAt, expiresAt
```

Each device replaces its daily document on retry rather than incrementing remote counters. This avoids duplicate uploads. Family cards add device screen-time/short-video totals; steps use one latest snapshot per person/day. The database is private by default and has no general write rule. Own-summary deletion remains permitted after departure so a member can clean up their records. A missing or expired invitation cannot create membership. The creator's first membership is allowed only in the same atomic batch that creates the family.

## Documented platform constraints

- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager): `PACKAGE_USAGE_STATS` requires an explicit Settings grant. Detailed events are kept for a few days, not an indefinite archive.
- [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event): lifecycle, screen-interactive, keyguard and shutdown events are available. They describe Android/app state, not individual watched reels.
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): lifecycle is system-managed and enabling the service requires the user's Settings action. Screen content is conditional on declared retrieval capabilities.
- [Sensitive accessibility data](https://developer.android.com/reference/androidx/core/view/accessibility/AccessibilityNodeInfoCompat#setAccessibilityDataSensitive(boolean)): protected nodes may be unavailable to a service with `isAccessibilityTool=false`; the app uses that truthful setting.
- [WorkManager periodic requests](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest): 15-minute minimum; Doze/battery policies can delay execution.
- [Android 15 background-service limits](https://developer.android.com/about/versions/15/behavior-changes-15#data-sync-fgs-timeout): an endless dataSync foreground service is not a valid substitute for scheduled uploads.
- [Samsung app management](https://developer.samsung.com/mobile/app-management.html): sleeping/deep sleeping can affect background work; user-managed exceptions are available.
- [Restricted settings](https://support.google.com/android/answer/12623953): sideloaded builds can require an extra Settings approval.
- [Android 17 local-network permission](https://developer.android.com/privacy-and-security/local-network-permission): emulator-backed integration tests need a debug-only local-network permission grant and a cleartext exception limited to `10.0.2.2`. The real cloud connection uses HTTPS and release builds declare neither exception.
- [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491) and [monitoring policy](https://support.google.com/googleplay/android-developer/answer/9888380): private household prototyping does not establish Play eligibility for adult-family monitoring.

## Deliberate first-version scope

This is an opt-in household prototype. There is no blocking of other apps, anti-uninstall behavior, hidden tracking, remote permission enabling, root requirement, or continuous screen recording. No live Firebase project is configured in the repository. Production release also needs account deletion, retention policy, release signing/update strategy, invitation management/revocation, and device-level reliability work.

Step sharing uses `families/{familyId}/steps/{uid}_{date}`: one canonical reading per person/date. Transactions reject stale overwrites; corrections replace totals rather than adding them. Raw health records are not uploaded. The Steps sharing toggle is separate from general family sharing. See `STEPS_INTEGRATION.md`.
