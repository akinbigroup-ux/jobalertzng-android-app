# JobAlertzNg Android app starter (free PWA + push notifications)

This project is an installable Progressive Web App for **https://www.jobalertz.com.ng/**. It has a bottom navigation bar (Home, Jobs, Career, Stories, More), a Blogger feed reader, search, an app install prompt, and opt-in push notifications for new posts.

## What this is — and what it is not

- On Android, visitors open the hosted app in Chrome and choose **Install app** or **Add to Home screen**. Chrome can install a PWA as a WebAPK on supported Android devices; the app then has its own launcher icon and standalone window.
- This package is **not a signed `.apk` file** and is not published in Google Play. It is the no-Play-Store route and does not require a Play Store developer account.
- Push alerts work only after a visitor taps **Enable alerts** and grants notification permission. Push capability depends on the browser/device, network access and notification settings.
- The checker runs every five minutes through GitHub Actions. Scheduled runs can be delayed by GitHub; do not promise exact instant delivery.
- The Firebase Spark plan has finite free quotas. Firebase currently lists Cloud Messaging as no-cost, Hosting as 10 GB storage and 10 GB/month transfer at no cost, and Firestore as 1 GiB storage, 50,000 reads/day and 20,000 writes/day. Keep the project on Spark and monitor usage. If quotas are exceeded, the free service may be limited/disabled; do not upgrade to a paid plan if the zero-cost requirement is strict.

## 1. Create the free Firebase project

1. Open https://console.firebase.google.com/ and create a project named `JobAlertzNg App`.
2. Stay on the **Spark (no-cost)** plan. Do not add a billing account or upgrade to Blaze.
3. In Project settings, add a **Web app**. Register it as `JobAlertzNg Android App`.
4. Copy the web app configuration object shown by Firebase.
5. In **Authentication → Sign-in method**, enable **Anonymous** sign-in.
6. In **Firestore Database**, create the default database. Choose a region and keep within the free quota.
7. In **Project settings → Cloud Messaging → Web Push certificates**, generate a key pair. Copy the **public key** (VAPID key).
8. Do not add a private server key to the website. Do not share service-account JSON or private keys in chat.

## 2. Add your Firebase browser configuration

Open `public/firebase-config.js` and replace every `PASTE_...` placeholder:

- `apiKey`, `authDomain`, `projectId`, `storageBucket`, `messagingSenderId`, `appId`: copy from the Firebase web-app config.
- `JANZ_VAPID_KEY`: paste the **public Web Push certificate key** from Cloud Messaging.
- `JANZ_APP_URL`: after first hosting deployment, change this to your real Firebase Hosting URL, such as `https://YOUR-PROJECT-ID.web.app`.

Firebase's web config and public VAPID key are client-side values. The service-account private key is not.

## 3. Deploy to Firebase Hosting (free Spark tier)

Install Node.js LTS and Firebase CLI on your computer:

```bash
npm install -g firebase-tools
```

Open a terminal in this project folder and run:

```bash
firebase login
firebase use --add
```

Choose the Firebase project you created. Then deploy the app and its Firestore rules:

```bash
firebase deploy --only hosting,firestore:rules
```

Firebase prints a Hosting URL ending in `.web.app`. Put that exact URL in `public/firebase-config.js` as `JANZ_APP_URL`, then run the deploy command again.

## 4. Set up automatic new-post notifications

The included GitHub Actions workflow checks the public Blogger feed every five minutes and sends an FCM push for each new post it detects (job, career advice, story or other article).

1. Create a **public** GitHub repository for this app and upload the project files. Standard GitHub-hosted Actions runners are free and unlimited for public repositories. Keep this repository free of private keys and user data.
2. In Firebase Project settings → Service accounts, generate a private key for the project service account. Keep the downloaded JSON private.
3. In GitHub repository → Settings → Secrets and variables → Actions → New repository secret, create:
   - Name: `FIREBASE_SERVICE_ACCOUNT_JSON`
   - Value: the complete service-account JSON file contents.
4. In the same Actions settings, open **Variables** and create:
   - Name: `APP_URL`
   - Value: your Firebase Hosting URL, e.g. `https://YOUR-PROJECT-ID.web.app`.
5. Ensure the workflow file `.github/workflows/notify-blog.yml` is on the repository's default branch and Actions are enabled.
6. Open **Actions → Notify JobAlertzNg subscribers about new Blogger posts → Run workflow** once. The first run only stores the latest Blogger post as a baseline; it deliberately does not send notifications for old posts.
7. After that, scheduled runs check for newer posts. A visitor receives alerts only after enabling them inside the app.

The service-account JSON is powerful. Store it only as the GitHub Actions secret. Never put it in `public/`, commit it, or send it to anyone.

## 5. Install on Android

1. Open the Firebase Hosting URL in **Chrome on Android**.
2. Tap **Install app** if the button appears, or open Chrome's three-dot menu and choose **Install app** / **Add to Home screen**.
3. Open JobAlertzNg from the new app icon.
4. Tap **Enable alerts** and allow notifications.
5. Publish a new public Blogger post. After the scheduled checker detects it, the opted-in device should receive a notification.

Some Android browser/device combinations create a WebAPK; others add a home-screen shortcut. Both open the app-like PWA, but only a WebAPK is a system-installed Android package.

## 6. Firestore security

The `firestore.rules` file allows an anonymous Firebase user to write only its own push token document. App visitors cannot read the subscriber list or the notifier checkpoint. The GitHub workflow uses the service account to read subscriber tokens and send notifications; Admin SDK access bypasses client rules.

Deploying the rules is included in the Firebase deploy command. Do not replace them with open read/write rules.

## Current app sections

- **Home:** recent Blogger posts.
- **Jobs:** posts classified through Blogger labels/titles such as Lagos, Abuja, Others, jobs and vacancies.
- **Career:** Career Advice labels.
- **Stories:** Job Stories / Beyond the Office Series labels or titles.
- **More:** Lagos, Abuja, Other States, Interview Experiences, Closing Soon, Expired Jobs, Beyond the Office Series and Contact links.

The feed is read using Blogger's public JSON-in-script feed. For best classification, keep labels consistent on new Blogger posts (for example `Lagos`, `Abuja`, `Others`, `Career Advice`, `Job Stories`).

## Troubleshooting

- **The feed is empty:** check that the public Blogger feed is enabled and that the blog is publicly accessible.
- **Enable alerts shows Firebase setup is incomplete:** confirm `public/firebase-config.js` has no remaining `PASTE_` values, deploy again, and check that Anonymous sign-in is enabled.
- **No push arrives:** check browser notification permission, the GitHub Actions run log, the `FIREBASE_SERVICE_ACCOUNT_JSON` secret, the `APP_URL` variable, and Firebase Cloud Messaging setup.
- **The first run sends no notification:** that is intentional. It initializes the checkpoint so visitors are not spammed with old posts.
- **A new post is not detected:** the checker reads the newest 50 published Blogger feed entries. A scheduled workflow can be delayed, and an unusually large number of posts between checks may exceed that scan window.

## Free service limits

There is no Play Store publishing fee because this project is installed directly from the HTTPS app site. Firebase lists FCM as a no-cost product; Firebase Hosting and Firestore still have free quotas, not unlimited free hosting. GitHub Actions standard runners are free for public repositories. Internet/data charges from the user's mobile network are separate and are not controlled by this app.
