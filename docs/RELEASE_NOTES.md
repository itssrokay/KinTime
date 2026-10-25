# KinTime 0.2.0

Adds a Clockify-inspired activity log to the family screen-time app.

- Describe an activity, add an optional project/category, and start/stop a timer.
- Running timer start times persist on the phone when the app closes or restarts.
- Add completed activities manually using date/time pickers.
- Review daily and weekly totals with history grouped by week and day.
- Continue an earlier activity as a new timer, edit entries, or delete them.
- Existing screen-time data is preserved by a tested Room database upgrade.

The Log tab works offline without Usage Access or Accessibility. Manual logs stay on the phone and are separate from automatically measured screen time and shared family totals.

## Install

Download **KinTime-android.apk** from this release on an Android 15+ phone. Allow installation from your browser if Android asks. This is a development build signed with the local Android debug certificate; an existing KinTime build can be updated in place only if its signing certificate matches. **SHA256SUMS** is included for download verification.

Screen-time tracking is enabled separately in Tracking → Usage Access. Optional Reels/Shorts estimates require consent and Accessibility setup, and still need device-level accuracy testing. Family sharing requires your own Firebase project; see the README and Firebase setup guide.

## Checks

APK build, Android lint, 16 calculation/validation tests, 5 Android UI/database tests, and the two-account Firebase emulator sync flow. The cloud permission rules retain their 11-test security suite.
