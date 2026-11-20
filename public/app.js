(function () {
  "use strict";

  const BLOG_URL = "https://www.jobalertz.com.ng";
  const FEED_URL = BLOG_URL + "/feeds/posts/default?alt=json-in-script&max-results=50&orderby=published&callback=janzBloggerFeedCallback";
  const state = { posts: [], tab: "home", query: "", shown: 12, currentPost: null };
  const $ = (id) => document.getElementById(id);
  const list = $("post-list");
  const statusLine = $("status-line");
  const feedSection = $("feed-section");
  const moreSection = $("more-section");
  const detailSection = $("detail-section");
  const config = window.JANZ_FIREBASE_CONFIG || {};
  let deferredInstallPrompt = null;
  let toastTimer = null;

  function toast(message) {
    const el = $("toast");
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 3300);
  }

  function safeText(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function plainText(html) {
    const doc = new DOMParser().parseFromString(String(html || ""), "text/html");
    doc.querySelectorAll("script,style,iframe,object,embed").forEach((node) => node.remove());
    return safeText(doc.body.textContent || "");
  }

  function postUrl(entry) {
    const alternate = (entry.link || []).find((link) => link.rel === "alternate");
    return alternate ? alternate.href : "";
  }

  function postImage(entry) {
    let image = entry.media$thumbnail && entry.media$thumbnail.url;
    if (!image) {
      const html = entry.content && entry.content.$t || entry.summary && entry.summary.$t || "";
      const doc = new DOMParser().parseFromString(html, "text/html");
      const img = doc.querySelector("img");
      if (img) image = img.getAttribute("src") || img.getAttribute("data-src");
    }
    return image ? image.replace(/\/s\d+(-c)?\//, "/s640/") : "";
  }

  function classify(labels, title) {
    const l = labels.join(" ").toLowerCase();
    const t = title.toLowerCase();
    if (/job stories|beyond the office|fiction|story series/.test(l + " " + t)) return "Story";
    if (/career advice|career development|career tips/.test(l)) return "Career Advice";
    if (/lagos|abuja|others|jobs|job alert|vacanc|recruitment|employment|closing soon|expired jobs/.test(l)) return "Job Alert";
    if (/job vacancy|job alert|recruitment|hiring|vacancy|vacancies|apply now|job opportunity/.test(t)) return "Job Alert";
    return "Article";
  }

  function parseEntry(entry) {
    const title = safeText(entry.title && entry.title.$t) || "JobAlertzNg update";
    const url = postUrl(entry);
    if (!url) return null;
    const labels = (entry.category || []).map((c) => safeText(c.term)).filter(Boolean);
    const html = entry.summary && entry.summary.$t || entry.content && entry.content.$t || "";
    const dateValue = entry.published && entry.published.$t || entry.updated && entry.updated.$t || "";
    const date = dateValue ? new Date(dateValue) : null;
    return {
      id: entry.id && entry.id.$t || url,
      title, url, labels, kind: classify(labels, title),
      image: postImage(entry),
      excerpt: plainText(html).slice(0, 390),
      date: date && !Number.isNaN(date.getTime()) ? date : null,
      raw: entry
    };
  }

  function loadFeed() {
    statusLine.textContent = "Loading the latest posts from JobAlertzNg…";
    list.innerHTML = "";
    const old = document.getElementById("janz-feed-script");
    if (old) old.remove();
    window.janzBloggerFeedCallback = function (data) {
      const entries = data && data.feed && data.feed.entry || [];
      state.posts = entries.map(parseEntry).filter(Boolean).sort((a, b) => (b.date || 0) - (a.date || 0));
      state.shown = 12;
      render();
      statusLine.textContent = state.posts.length ? "Showing the latest public posts from JobAlertzNg." : "No published posts were returned by the Blogger feed.";
      const requested = new URLSearchParams(window.location.search).get("post");
      if (requested) {
        const match = state.posts.find((p) => p.url === requested);
        if (match) showArticle(match, false);
      }
    };
    const script = document.createElement("script");
    script.id = "janz-feed-script";
    script.src = FEED_URL;
    script.onerror = function () {
      statusLine.textContent = "We could not load the Blogger feed. Check your connection and tap refresh.";
      list.innerHTML = '<div class="empty-state">JobAlertzNg updates could not be loaded right now. Please try again.</div>';
    };
    document.body.appendChild(script);
  }

  function matchesTab(post) {
    if (state.tab === "home") return true;
    const labelText = post.labels.join(" ").toLowerCase();
    if (state.tab === "jobs") return post.kind === "Job Alert" || /lagos|abuja|others|jobs|vacanc|recruitment/.test(labelText);
    if (state.tab === "career") return post.kind === "Career Advice" || /career advice|career development/.test(labelText);
    if (state.tab === "stories") return post.kind === "Story" || /job stories|beyond the office|fiction/.test(labelText);
    return true;
  }

  function dateLabel(date) {
    return date ? date.toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" }) : "Recently published";
  }

  function makeCard(post) {
    const card = document.createElement("a");
    card.className = "post-card";
    card.href = post.url;
    card.setAttribute("aria-label", "Open " + post.title);
    card.addEventListener("click", (event) => {
      event.preventDefault();
      showArticle(post, true);
    });

    const thumb = document.createElement("span");
    thumb.className = "post-thumb";
    if (post.image) {
      const img = document.createElement("img");
      img.src = post.image;
      img.alt = "";
      img.loading = "lazy";
      img.onerror = () => { thumb.innerHTML = "JA"; };
      thumb.appendChild(img);
    } else {
      thumb.textContent = "JA";
    }

    const info = document.createElement("span");
    info.className = "post-info";
    const label = document.createElement("span");
    label.className = "post-label";
    label.textContent = post.kind;
    const title = document.createElement("span");
    title.className = "post-title";
    title.textContent = post.title;
    const meta = document.createElement("span");
    meta.className = "post-meta";
    const date = document.createElement("span");
    date.textContent = dateLabel(post.date);
    const location = document.createElement("span");
    const place = post.labels.find((l) => /lagos|abuja|others/i.test(l));
    location.textContent = place || "Nigeria";
    meta.append(date, location);
    info.append(label, title, meta);
    card.append(thumb, info);
    return card;
  }

  function render() {
    const filtered = state.posts.filter((post) => {
      const queryMatch = !state.query || (post.title + " " + post.excerpt + " " + post.labels.join(" ")).toLowerCase().includes(state.query);
      return matchesTab(post) && queryMatch;
    });
    const titles = { home: ["THE LATEST", "Latest updates", "New opportunities and useful reads from JobAlertzNg."], jobs: ["FIND YOUR NEXT ROLE", "Latest jobs", "Explore job vacancies and recruitment updates."], career: ["GROW YOUR CAREER", "Career advice", "Guidance, tips and resources for your working life."], stories: ["BEYOND THE ORDINARY", "Job stories", "Workplace stories and experiences from JobAlertzNg."] };
    const copy = titles[state.tab] || titles.home;
    $("section-eyebrow").textContent = copy[0];
    $("feed-title").textContent = copy[1];
    $("feed-description").textContent = copy[2];
    $("post-count").textContent = filtered.length + (filtered.length === 1 ? " post" : " posts");
    list.innerHTML = "";
    const visible = filtered.slice(0, state.shown);
    visible.forEach((post) => list.appendChild(makeCard(post)));
    if (!visible.length) list.innerHTML = '<div class="empty-state">No matching posts yet. Try another section or search term.</div>';
    $("load-more").hidden = filtered.length <= state.shown;
  }

  function showArticle(post, pushHistory) {
    state.currentPost = post;
    feedSection.hidden = true;
    moreSection.hidden = true;
    detailSection.hidden = false;
    $("article-detail").innerHTML = "";
    const article = $("article-detail");
    if (post.image) {
      const img = document.createElement("img");
      img.className = "detail-image";
      img.src = post.image;
      img.alt = "";
      article.appendChild(img);
    }
    const content = document.createElement("div");
    content.className = "detail-content";
    const kicker = document.createElement("div");
    kicker.className = "detail-kicker";
    kicker.textContent = post.kind;
    const title = document.createElement("h1");
    title.textContent = post.title;
    const date = document.createElement("div");
    date.className = "detail-date";
    date.textContent = dateLabel(post.date) + (post.labels.length ? "  •  " + post.labels.slice(0, 3).join(", ") : "");
    const excerpt = document.createElement("p");
    excerpt.className = "detail-excerpt";
    excerpt.textContent = post.excerpt || "Open the full article to read this JobAlertzNg update.";
    const actions = document.createElement("div");
    actions.className = "detail-actions";
    const read = document.createElement("a");
    read.href = post.url;
    read.target = "_blank";
    read.rel = "noopener";
    read.textContent = "Read full article";
    const share = document.createElement("button");
    share.type = "button";
    share.textContent = "Share post";
    share.addEventListener("click", () => sharePost(post));
    actions.append(read, share);
    content.append(kicker, title, date, excerpt, actions);
    article.appendChild(content);
    if (pushHistory) history.pushState({ post: post.url }, "", "?post=" + encodeURIComponent(post.url));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function showFeed() {
    detailSection.hidden = true;
    moreSection.hidden = state.tab !== "more";
    feedSection.hidden = state.tab === "more";
    if (state.tab !== "more") render();
    state.currentPost = null;
    history.replaceState({}, "", window.location.pathname);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function setTab(tab) {
    state.tab = tab;
    state.shown = 12;
    document.querySelectorAll(".nav-item").forEach((button) => {
      const active = button.dataset.tab === tab;
      button.classList.toggle("active", active);
      if (active) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    });
    showFeed();
  }

  function sharePost(post) {
    const data = { title: post.title, text: "Read this update on JobAlertzNg", url: post.url };
    if (navigator.share) navigator.share(data).catch(() => {});
    else if (navigator.clipboard) navigator.clipboard.writeText(post.url).then(() => toast("Article link copied."));
    else toast("Copy the article link from the address bar.");
  }

  document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => setTab(button.dataset.tab)));
  document.querySelectorAll("[data-quick]").forEach((button) => button.addEventListener("click", () => setTab(button.dataset.quick)));
  $("hero-jobs-button").addEventListener("click", () => setTab("jobs"));
  $("back-to-feed").addEventListener("click", () => showFeed());
  $("refresh-button").addEventListener("click", () => loadFeed());
  $("load-more").addEventListener("click", () => { state.shown += 12; render(); });
  $("search-input").addEventListener("input", (event) => {
    state.query = event.target.value.toLowerCase().trim();
    $("clear-search").hidden = !state.query;
    render();
  });
  $("clear-search").addEventListener("click", () => {
    $("search-input").value = "";
    state.query = "";
    $("clear-search").hidden = true;
    render();
  });
  window.addEventListener("popstate", () => {
    const requested = new URLSearchParams(location.search).get("post");
    const post = state.posts.find((p) => p.url === requested);
    if (post) showArticle(post, false); else showFeed();
  });

  /* PWA INSTALL PROMPT */
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    $("install-button").hidden = false;
  });
  $("install-button").addEventListener("click", async () => {
    if (!deferredInstallPrompt) {
      toast("In Chrome, open the menu and choose Install app or Add to Home screen.");
      return;
    }
    deferredInstallPrompt.prompt();
    await deferredInstallPrompt.userChoice;
    deferredInstallPrompt = null;
    $("install-button").hidden = true;
  });
  window.addEventListener("appinstalled", () => {
    $("install-button").hidden = true;
    toast("JobAlertzNg has been installed.");
  });

  function firebaseConfigured() {
    return config && config.apiKey && !String(config.apiKey).includes("PASTE_") && window.JANZ_VAPID_KEY && !String(window.JANZ_VAPID_KEY).includes("PASTE_");
  }

  async function enableAlerts() {
    if (!firebaseConfigured()) {
      toast("Push alerts need your Firebase web config and VAPID key first. See the setup guide.");
      return;
    }
    if (!window.isSecureContext || !("Notification" in window) || !("serviceWorker" in navigator) || !window.firebase) {
      toast("Push alerts are not supported in this browser. Try Chrome on Android.");
      return;
    }
    try {
      if (!(await firebase.messaging.isSupported())) {
        toast("This browser does not support Firebase web push.");
        return;
      }
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        toast("Notifications were not enabled. You can change this in browser settings.");
        return;
      }
      const registration = await navigator.serviceWorker.register("/firebase-messaging-sw.js");
      const auth = firebase.auth();
      if (!auth.currentUser) await auth.signInAnonymously();
      const messaging = firebase.messaging();
      const token = await messaging.getToken({ vapidKey: window.JANZ_VAPID_KEY, serviceWorkerRegistration: registration });
      if (!token) throw new Error("No push token was returned.");
      await firebase.firestore().collection("subscribers").doc(auth.currentUser.uid).set({
        token,
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
        platform: /Android/i.test(navigator.userAgent) ? "android-web" : "web"
      }, { merge: true });
      $("alerts-button-text").textContent = "Alerts enabled";
      $("alerts-button").classList.add("alerts-enabled");
      toast("You're subscribed. New JobAlertzNg posts can now notify this device.");
      messaging.onMessage((payload) => {
        const data = payload.data || {};
        toast(data.title || "A new JobAlertzNg post is available.");
      });
    } catch (error) {
      console.error("Push setup error:", error);
      toast("Could not enable alerts. Check Firebase setup and try again.");
    }
  }
  $("alerts-button").addEventListener("click", enableAlerts);

  if ("serviceWorker" in navigator && window.isSecureContext) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("/firebase-messaging-sw.js").catch((error) => console.warn("Service worker registration failed", error));
    });
  }

  loadFeed();
})();
