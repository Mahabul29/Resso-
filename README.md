# EvaMusic

A Resso-style music app for Android, built with Kotlin and Jetpack Compose.

- Swipe feed: swipe up for the next song, swipe down for the previous one
- Explore: search songs and artists, browse trending
- Me: liked songs, followed artists, listening history
- Comments on every song, saved in Firebase
- Share the whole app with friends
- Background playback with notification controls

## Download the APK

The APK is built automatically by GitHub Actions every time code is pushed.

1. Open this repository on GitHub and tap the **Actions** tab.
2. Tap the latest run with a green check mark (the top one in the list).
3. Scroll down to **Artifacts** and tap **EvaMusic-debug-apk**. A `.zip` file downloads (you must be signed in to GitHub).
4. Open the `.zip` (in your Files app) and extract it. Inside is `app-debug.apk`.
5. Tap `app-debug.apk` to install it.

### Installing

Android will ask you to allow installs from unknown sources the first time. Tap **Settings**, allow it for your Files app or browser, then go back and tap **Install**.

> Artifacts are kept for 30 days. If the link has expired, run the build again (see below).

## Build it yourself

You don't need a computer. On GitHub:

1. Go to **Actions** → **Build Android APK**.
2. Tap **Run workflow**, choose `debug`, and confirm.
3. Wait for the run to finish (usually 5 to 10 minutes), then download the APK as shown above.

To build on a computer instead, install Android Studio, open this project, and run **Build → Build APK(s)**.

## Setup (one time)

Add these in the repo under **Settings → Secrets and variables → Actions**:

| Secret | What it is | Required |
| --- | --- | --- |
| `GOOGLE_SERVICES_JSON` | Full contents of your Firebase `google-services.json` | For comments and login |
| `DOT_ENV` | Contents of a `.env` file (for example `GEMINI_API_KEY=...`) | Only if the app uses it |
| `KEYSTORE_BASE64` | Your `.jks` signing key encoded as base64 | Release builds only |
| `STORE_PASSWORD` | Keystore password | Release builds only |
| `KEY_PASSWORD` | Key password (the key alias must be `upload`) | Release builds only |

Without `GOOGLE_SERVICES_JSON` the app still builds and plays music, but Firebase features such as comments will not work.

### Firebase

1. In the Firebase console, enable **Authentication → Anonymous**.
2. Create a **Firestore Database**.
3. Paste the contents of `firestore.rules` from this repo into the Firestore **Rules** tab and publish.

## Notes

- There is no download feature in the app, by design. Songs are streamed only.
- Debug APKs are for testing. Use a release build (with your own keystore) for sharing widely.
- 
