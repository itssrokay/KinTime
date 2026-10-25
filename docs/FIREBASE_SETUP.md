# Enable family sharing

Local tracking requires no cloud account. This enables the implemented Family tab for your household.

1. Open [Firebase Console](https://console.firebase.google.com/) and create a project. Google Analytics is optional and is not included in this app.
2. Add an **Android app** with package **`app.kintime.family`**. A SHA certificate is not needed for Email/Password authentication.
3. Open **Authentication → Sign-in method** and enable **Email/Password** (not email-link sign-in).
4. Open **Firestore Database → Create database**, choose an appropriate region, and create a Standard database in production mode. The default database ID must be `(default)`. Avoid open/test-mode rules.
5. In Project settings → Your apps, download the Android configuration. Copy these non-admin client values into `firebase.properties` at the project root, using `firebase.properties.example` as a template:

```properties
apiKey=<client[0].api_key[0].current_key from google-services.json>
applicationId=<client[0].client_info.mobilesdk_app_id>
projectId=<project_info.project_id>
```

`applicationId` here is the **Firebase mobile app ID**, usually starting `1:`. It is different from the Android package name. If there are multiple clients in the JSON, use the one whose `android_client_info.package_name` equals `app.kintime.family`. Configuration is loaded at build time. The project deliberately does not use the Google Services plugin, and does not require `google-services.json` to compile. No Admin SDK/service-account secret belongs in this file.

6. Using Node 24.12+ (see `.nvmrc`), install the local test tooling and deploy only the Firestore rules/index definitions:

```sh
npm ci
npx firebase login
npm run test:rules
npx firebase deploy --only firestore:rules,firestore:indexes --project YOUR_PROJECT_ID
```

7. Rebuild and install the configured APK on every phone:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

8. Each person sets their display name in Tracking, creates their own account in Family, and signs in. The first person creates a family, then shares a 24-hour invitation code. Each other person joins with that code. Statistics are still private until **Share my daily totals** is enabled.

9. Verify with two accounts that family statistics appear, outsiders cannot access the group, pause removes shared records, and leaving removes membership. The Firestore emulator rules tests cover the access-control contract; they do not establish a connection to your new live project.

Use Firebase's usage and budget controls for your project. No Firebase resources have been provisioned or deployed by this repository. The Family tab explicitly reports an unconfigured build until all three client configuration values are present.

Official references: [Firebase Android setup](https://firebase.google.com/docs/android/setup), [Email/Password authentication](https://firebase.google.com/docs/auth/android/password-auth), [Firestore rules](https://firebase.google.com/docs/firestore/security/rules-conditions), [emulator rules testing](https://firebase.google.com/docs/rules/unit-tests).
