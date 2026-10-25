# KinTime 0.3.0 — Steps

Adds daily steps through Android Health Connect.

- Read combined step totals shared by Samsung Health / Galaxy Watch, Fitbit / Google Health, and other Health Connect step sources.
- Supported updated Android phones can count steps through Health Connect itself.
- New Steps screen with a daily goal, seven-day history, freshness labels, setup guidance, and privacy disclosure.
- Optional background step-read permission; otherwise steps refresh while KinTime is open.
- Separate opt-in for sharing daily step totals with family, plus a Steps leaderboard.
- One shared snapshot per person/day prevents adding duplicate totals across their devices.
- Database upgrades preserve existing screen-time and short-video data. Manual activity timers remain removed.

Download **KinTime-android.apk** for Android 15+. This is a development build signed with the same local debug certificate as earlier releases. **SHA256SUMS** is included.

Connect in **Steps → Connect step data**. For Samsung/Fitbit data, allow the source health app to write Steps into Health Connect and allow KinTime to read them. Watch syncing and system updates affect availability/freshness. Local steps work without Firebase; family step sharing requires the updated Firestore rules and your own Firebase project. See the README's Steps setup guide and official-source research.
