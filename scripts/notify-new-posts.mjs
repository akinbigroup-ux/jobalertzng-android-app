import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const BLOG_FEED = "https://www.jobalertz.com.ng/feeds/posts/default?alt=json&max-results=50&orderby=published";
const APP_URL = process.env.APP_URL;
const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

if (!rawServiceAccount) throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON secret.");
if (!APP_URL || !/^https:\/\//i.test(APP_URL)) throw new Error("Set the APP_URL GitHub repository variable to your Firebase Hosting URL.");

const serviceAccount = JSON.parse(rawServiceAccount);
initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();
const messaging = getMessaging();
const checkpointRef = db.collection("appState").doc("blogger-feed-checkpoint");

function getEntryUrl(entry) {
  const alternate = (entry.link || []).find((link) => link.rel === "alternate");
  return alternate?.href || "";
}

function getPublishedMs(entry) {
  const value = entry.published?.$t || entry.updated?.$t || "";
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function getKind(entry) {
  const labels = (entry.category || []).map((item) => String(item.term || "").toLowerCase()).join(" ");
  const title = String(entry.title?.$t || "").toLowerCase();
  if (/career advice|career development|career tips/.test(labels)) return "career advice";
  if (/job stories|beyond the office|fiction|story series/.test(labels + " " + title)) return "workplace story";
  if (/lagos|abuja|others|jobs|job alert|vacanc|recruitment|employment/.test(labels) || /job vacancy|job alert|recruitment|vacancy|vacancies|hiring|job opportunity/.test(title)) return "job vacancy";
  return "article";
}

function buildAppLink(postUrl) {
  const url = new URL("/", APP_URL);
  url.searchParams.set("post", postUrl);
  return url.toString();
}

async function sendToSubscribers(post) {
  const snapshot = await db.collection("subscribers").get();
  const targets = snapshot.docs
    .map((doc) => ({ id: doc.id, token: doc.data().token }))
    .filter((item) => typeof item.token === "string" && item.token.length > 50);

  if (!targets.length) {
    console.log(`No opted-in devices yet; skipping push for: ${post.title}`);
    return;
  }

  const kind = getKind(post.entry);
  const title = kind === "job vacancy" ? "New Job Alert on JobAlertzNg"
    : kind === "career advice" ? "New Career Advice on JobAlertzNg"
    : kind === "workplace story" ? "New Story on JobAlertzNg"
    : "New Article on JobAlertzNg";
  const body = post.title.length > 115 ? post.title.slice(0, 112) + "..." : post.title;
  const link = buildAppLink(post.url);
  const chunks = [];

  for (let i = 0; i < targets.length; i += 500) chunks.push(targets.slice(i, i + 500));

  for (const chunk of chunks) {
    const response = await messaging.sendEachForMulticast({
      tokens: chunk.map((item) => item.token),
      data: {
        title,
        body,
        link,
        postId: String(post.id || post.url),
        category: kind
      },
      webpush: { headers: { TTL: "86400" } }
    });

    const stale = [];
    response.responses.forEach((result, index) => {
      if (!result.success) {
        const code = result.error?.code || "";
        if (code.includes("registration-token-not-registered") || code.includes("invalid-registration-token")) {
          stale.push(chunk[index].id);
        }
      }
    });
    await Promise.all(stale.map((id) => db.collection("subscribers").doc(id).delete()));
    console.log(`Push sent for "${post.title}": ${response.successCount} successful, ${response.failureCount} failed.`);
  }
}

async function main() {
  const response = await fetch(BLOG_FEED, { headers: { "user-agent": "JobAlertzNg-PWA-Notifier/1.0" } });
  if (!response.ok) throw new Error(`Blogger feed returned HTTP ${response.status}`);
  const json = await response.json();
  const entries = (json.feed?.entry || []).filter((entry) => getEntryUrl(entry));
  if (!entries.length) {
    console.log("Blogger feed is empty; nothing to do.");
    return;
  }

  const newest = entries[0];
  const newestId = newest.id?.$t || getEntryUrl(newest);
  const newestMs = getPublishedMs(newest);
  const checkpoint = await checkpointRef.get();

  if (!checkpoint.exists) {
    await checkpointRef.set({ lastPostId: newestId, lastPublishedMs: newestMs, initializedAt: new Date().toISOString() });
    console.log("First run: saved the latest Blogger post as a baseline. No old posts were sent.");
    return;
  }

  const previous = checkpoint.data();
  const previousId = previous.lastPostId || "";
  const previousMs = Number(previous.lastPublishedMs || 0);
  const foundPrevious = entries.findIndex((entry) => (entry.id?.$t || getEntryUrl(entry)) === previousId);
  let freshEntries;

  if (foundPrevious >= 0) {
    freshEntries = entries.slice(0, foundPrevious).reverse();
  } else {
    freshEntries = entries.filter((entry) => getPublishedMs(entry) > previousMs)
      .sort((a, b) => getPublishedMs(a) - getPublishedMs(b));
  }

  if (!freshEntries.length) {
    console.log("No new Blogger posts.");
    return;
  }

  for (const entry of freshEntries) {
    const id = entry.id?.$t || getEntryUrl(entry);
    const url = getEntryUrl(entry);
    const title = String(entry.title?.$t || "New JobAlertzNg post");
    const publishedMs = getPublishedMs(entry);
    if (!url) continue;

    await sendToSubscribers({ id, url, title, entry });
    await checkpointRef.set({ lastPostId: id, lastPublishedMs: publishedMs, lastPostUrl: url, checkedAt: new Date().toISOString() }, { merge: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
