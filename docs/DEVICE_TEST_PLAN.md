# Household trial

Automated tests establish calculation and permission-rule behavior. Only physical devices establish recogniser coverage and real battery impact. Run on at least one Android 15 phone and the Android 17 / One UI 9 phone.

## Screen time

- Start with no Usage Access; confirm the app asks for access and does not invent a total.
- Enable Usage Access manually and return; confirm recent events appear.
- Use a stopwatch: spend 2 minutes in one app, 1 minute in another, then lock the phone for 1 minute. Refresh and compare with the measured intervals.
- Exercise screen off, notifications waking the display, lock-screen interactions, launcher time, split screen, picture-in-picture, and multiple activities within one app.
- Cross midnight in the device's time zone; confirm sessions split into the correct days. Do not alter the date on your everyday phone just to test this.
- Reboot, battery saver, force stop, revoke Usage Access, and restore permission. Check for stale/partial data, not an assumption of zero.

## Reels / Shorts

- Enable only after the explicit disclosure, then manually enable the Android accessibility service.
- Record app version, device OS/build, language, detector status, and manual tally for each session.
- View 20 videos for at least 3 seconds each; compare observed counts and time. Then try rapid skips, revisits, loops, ads, comments, shared links, app switches, and locking the screen.
- Browse normal Instagram feeds and ordinary YouTube videos; confirm these do not count as short-video views.
- If a native viewer is not recognised, the Tracking tab should say no recognised viewer/interface may be unsupported. A captured UI hierarchy from a consented test session can guide a detector update; never upload messages or account-sensitive text into the repository.
- Leave the service on for 24 hours, compare battery use, and test Samsung sleeping-app behavior.
- Record absolute count error and recognised-time coverage per platform. Do not claim an accuracy percentage before these measurements.

## Steps / Health Connect

- In Steps, read the disclosure, deny access, then grant only read Steps. Confirm that no medical, route, heart-rate, or sleep permission is requested. Open the privacy policy from the Health Connect permission screen.
- In Samsung Health or Fitbit/Google Health, enable writing Steps into Health Connect. Sync the watch/tracker and compare KinTime's refreshed daily total with Health Connect's aggregate. A source app's own total can differ because of source priorities or sync timing.
- On a supported updated phone, test native Health Connect phone steps without a connected watch. Walk a manually counted route with the phone carried. Compare later; do not assume native counting is supported from the Android major version alone.
- Test overlapping watch and phone sources and change source priority. Confirm KinTime uses the aggregate rather than adding overlapping records.
- Check yesterday, a measured zero, a day with no records, source corrections/deletions, device time-zone changes, and midnight. No records should show a blank reading.
- Deny background permission: steps should refresh on opening KinTime. Grant it if available and check a later periodic update, allowing for Android scheduling and watch-sync delays.
- Share steps with another family account; verify the leaderboard, read timestamps, and separate step-sharing switch. Sign in on a second device and confirm one person/day snapshot, not summed totals.
- Turn off step sharing or disconnect online; verify this device's step snapshot is removed while screen-time sharing remains. Repeat offline, restart the app, and reconnect; pending cleanup must retry even if master sharing is off.
- Revoke Steps from Android settings. Refresh and verify no new health reads/uploads, cached readings remain marked with their original read time, and this device's uploaded steps are removed after successful cleanup.

## Family sharing

- Configure/deploy Firebase per the setup guide, using two separate accounts on two phones.
- Create family, issue invite, join, and explicitly enable sharing on both. Verify totals and “updated” times.
- Disconnect network; confirm local collection continues. Reconnect and verify a retry replaces the daily document without doubling totals.
- Use a third account outside the family; confirm private data cannot be read and guessed/expired invitations do not join.
- Turn off app breakdowns and confirm existing remote app maps are cleared. Turn off sharing and confirm all that account's summaries are removed.
- Leave/sign out online; confirm shared data removal and loss of access. Attempt offline; confirm the app reports that cleanup needs retry.
- Remember that other devices signed in to the same account can still upload if their sharing is enabled. Pause on each device when ending sharing entirely.

## Upgrade checks

- Update a 0.1.0, 0.2.0, or 0.2.1 installation with the same signing key. Confirm that screen-time and short-video history are retained.
- Confirm navigation contains Today, Steps, Family, and Tracking, with no manual timer.

## Release readiness

Validate actual recogniser accuracy, Android 15 installation, battery use, Samsung permission flow, live backend configuration, and release signing before sending a household release. Review Play policies separately before any store submission.
