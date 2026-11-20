/*
  PUBLIC CLIENT CONFIGURATION.
  Replace these placeholders with the Firebase web-app config and Web Push
  public VAPID key from your own Firebase project.
  This file is served to app visitors; never put a private service-account key here.
*/
self.JANZ_FIREBASE_CONFIG = {
  apiKey: "PASTE_FIREBASE_WEB_API_KEY_HERE",
  authDomain: "PASTE_PROJECT_ID.firebaseapp.com",
  projectId: "PASTE_PROJECT_ID",
  storageBucket: "PASTE_PROJECT_ID.firebasestorage.app",
  messagingSenderId: "PASTE_MESSAGING_SENDER_ID",
  appId: "PASTE_FIREBASE_APP_ID"
};

self.JANZ_VAPID_KEY = "PASTE_PUBLIC_WEB_PUSH_VAPID_KEY_HERE";
self.JANZ_APP_URL = "https://PASTE_PROJECT_ID.web.app";
