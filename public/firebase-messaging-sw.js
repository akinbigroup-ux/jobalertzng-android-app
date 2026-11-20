/* JobAlertzNg PWA + Firebase Cloud Messaging service worker */
importScripts("https://www.gstatic.com/firebasejs/11.10.0/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/11.10.0/firebase-messaging-compat.js");
importScripts("/firebase-config.js");

const CACHE_NAME = "jobalertzng-app-shell-v1";
const APP_SHELL = ["/", "/index.html", "/styles.css", "/app.js", "/manifest.webmanifest", "/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-512-maskable.png"] ;

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (request.mode === "navigate") {
    event.respondWith(fetch(request).then((response) => {
      const copy = response.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put("/index.html", copy));
      return response;
    }).catch(() => caches.match("/index.html")));
    return;
  }
  event.respondWith(fetch(request).then((response) => {
    if (response.ok && url.pathname !== "/firebase-config.js") {
      const copy = response.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
    }
    return response;
  }).catch(() => {
    if (url.pathname === "/firebase-config.js") return Response.error();
    return caches.match(request);
  }));
});

if (self.JANZ_FIREBASE_CONFIG && self.JANZ_FIREBASE_CONFIG.apiKey && !String(self.JANZ_FIREBASE_CONFIG.apiKey).includes("PASTE_")) {
  firebase.initializeApp(self.JANZ_FIREBASE_CONFIG);
  const messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    const data = payload.data || {};
    const title = data.title || "New on JobAlertzNg";
    const options = {
      body: data.body || "A new job, career update or article has been published.",
      icon: "/icons/icon-192.png",
      badge: "/icons/icon-192.png",
      tag: data.postId || "jobalertzng-new-post",
      renotify: true,
      data: { url: data.link || "/" }
    };
    return self.registration.showNotification(title, options);
  });
}

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = event.notification.data && event.notification.data.url || "/";
  event.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
    for (const client of clientList) {
      if ("focus" in client && new URL(client.url).origin === self.location.origin) {
        client.navigate(target);
        return client.focus();
      }
    }
    return self.clients.openWindow(target);
  }));
});
