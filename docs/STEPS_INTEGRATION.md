# Steps integration and evidence

Researched against official Android, Samsung, and Google documentation on 25 October 2026.

## Supported data sources

KinTime reads Health Connect's aggregated daily step total using `StepsRecord.COUNT_TOTAL` and `aggregate()`. It requests read access to steps only, with optional background access. Android recommends aggregation for cumulative step data to avoid overlapping records from different apps. The user controls source priority in Health Connect.

| Source | How steps reach KinTime |
| --- | --- |
| Samsung Health / Galaxy Watch | Sync the watch into Samsung Health; allow Samsung Health to write Steps to Health Connect; grant KinTime read access. |
| Fitbit / Google Health | Sync the tracker to the health app, enable its Health Connect Steps-writing connection, and grant KinTime read access. |
| Google Fit / another app | Works when the installed app supports writing Steps to Health Connect and that connection is enabled. |
| Phone itself | Health Connect supports built-in phone step counting on Android 14+ with SDK extension 20+, after steps-read access is granted. Device/system support and updates matter. |

Official sources:

- [Android: reading data, mobile steps, aggregation, background permission](https://developer.android.com/health-and-fitness/health-connect/read-data). Native phone attribution changed in June 2026; unfiltered aggregate reads automatically include native phone steps. KinTime does not hardcode an old `android` origin or a device-specific synthetic package ID.
- [Android: automatically track phone steps](https://support.google.com/android/answer/16786157). Describes the Play system update requirement, automatic capture after read permission, and phone/source priority.
- [Samsung: Health Connect setup FAQ](https://developer.samsung.com/health/health-connect-faq.html). Grant Samsung Health permissions, open the app after granting, and sync Samsung account/watch data.
- [Samsung: supported Health Connect data](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect). “All steps” maps to `StepsRecord`.
- [Google: Health Connect with Google Health](https://support.google.com/googlehealth/answer/14506680). Google Health can write Steps; the receiving app needs permission to read them.
- [Android: source priority and overlapping data](https://support.google.com/android/answer/13770384).

No direct Fitbit/Samsung cloud API or vendor developer key is needed. Google's [current Fitbit migration notice](https://support.google.com/googlehealth/answer/14236613) states that legacy Fitbit Web API connections retire on 30 October 2026, while Health Connect connections are unaffected. This implementation uses Health Connect.

## Setup on each phone

1. Update Android, the Google Play system, Health Connect, and the source health app.
2. In **Samsung Health → Settings → Health Connect**, allow Samsung Health to write Steps, then open/sync Samsung Health and the watch. For Fitbit/Google Health, use its Health Connect connection settings and enable Steps writing.
3. In **KinTime → Steps → Connect step data**, read the disclosure and grant **read Steps** permission.
4. Return and tap **Refresh steps**. If no records exist, the UI shows a blank reading rather than inventing zero.
5. Optional: allow background step refresh if available. Otherwise, open KinTime to refresh. Android scheduling and source/watch sync can delay totals.
6. Optional: join a family, enable **Share my daily totals**, then separately enable **Share steps with family**. Family has a Steps leaderboard.

Local steps work without Firebase. Family step sharing requires the updated Firestore rules from this repository, deployed to your own Firebase project. KinTime cannot enable source-app writing or grant permissions for another person.

## Storage and sharing behavior

- Room stores only local date, time zone, daily aggregated count, and the read timestamp. Schema 3 → 4 preserves existing screen-time/video data.
- A read timestamp means when KinTime queried Health Connect, not when a watch last synced.
- Missing readings and measured zero are distinct. Successful reads returning no records remove outdated local totals for that date. Source corrections can decrease a count.
- Raw health records, source identifiers, routes, heart rate, and sleep are not uploaded or requested.
- Step sharing is off by default and requires separate consent alongside family sharing.
- The cloud has **one step document per person and local date**. A newer reading replaces the previous snapshot; counts are not added across devices. Use a primary phone if different phones contain different health stores: the latest reading wins, rather than merging those stores.
- Disabling step sharing or disconnecting removes this device's shared snapshots after successful online cleanup. Failed step-only cleanup is persisted and retried on the next sync even if sharing is paused. Other devices using the account may continue sharing. Pausing/leaving family sharing removes the account's step snapshots too.
- Disconnecting stops imports and revokes KinTime's Health Connect permissions. Saved local history remains until deleted; reconnecting may import recent data again. Imports stop immediately on disconnect. Android’s permission revocation can be asynchronous until the process can safely end; users can also remove access directly in Health Connect settings. See [Android runtime permission revocation](https://developer.android.com/training/permissions/requesting#remove-access).
- No source records means no fabricated total. Foreground reads do not silently turn into background reads: optional background access is checked separately.
- The in-app privacy policy is available from Steps and from the Android Health Connect permission screen.

## Verification boundaries

Repository tests cover consent/permission gates, background restrictions, zero versus missing data, corrections/deletions, and disconnection. Firestore tests cover ownership, family-only access, count bounds, stale writes, and one snapshot across devices. The Android/Firebase integration test covers publishing, replacing, and removing shared steps.

Actual Samsung/Fitbit watch syncing and physical step accuracy require a trial on the family's devices. Emulator/fake-source tests do not establish physical counting accuracy. Health Connect can be unavailable on unsupported profiles or need a system update.
