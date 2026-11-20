package com.jobalertzng.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PostCheckWorker extends Worker {
    public static final String CHANNEL_ID = "janz_new_posts";
    public static final String EXTRA_POST_URL = "com.jobalertzng.app.POST_URL";
    private static final String PREFS = "janz_post_alerts";
    private static final String LAST_POST_ID = "last_seen_post_id";
    private static final String FEED_URL =
            "https://www.jobalertz.com.ng/feeds/posts/default?alt=json&max-results=12";

    public PostCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            List<Post> posts = fetchLatestPosts();
            if (posts.isEmpty()) return Result.retry();

            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String lastSeen = prefs.getString(LAST_POST_ID, null);
            String newestId = posts.get(0).id;

            // First run establishes a baseline; it does not alert on old posts.
            if (lastSeen == null) {
                prefs.edit().putString(LAST_POST_ID, newestId).apply();
                return Result.success();
            }
            if (newestId.equals(lastSeen)) return Result.success();

            List<Post> freshPosts = new ArrayList<>();
            for (Post post : posts) {
                if (post.id.equals(lastSeen)) break;
                freshPosts.add(post);
                if (freshPosts.size() >= 3) break;
            }
            if (freshPosts.isEmpty()) freshPosts.add(posts.get(0));

            for (Post post : freshPosts) sendNotification(post);
            prefs.edit().putString(LAST_POST_ID, newestId).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private List<Post> fetchLatestPosts() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JobAlertzNg-Android/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Blogger feed HTTP " + code);
            }
            StringBuilder body = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            JSONObject root = new JSONObject(body.toString());
            JSONObject feed = root.optJSONObject("feed");
            if (feed == null) return new ArrayList<>();
            JSONArray entries = feed.optJSONArray("entry");
            List<Post> posts = new ArrayList<>();
            if (entries == null) return posts;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                JSONObject idObject = entry.optJSONObject("id");
                JSONObject titleObject = entry.optJSONObject("title");
                String id = idObject != null ? idObject.optString("$t", "") : "";
                String title = titleObject != null ? titleObject.optString("$t", "New article") : "New article";
                String url = findAlternateLink(entry.optJSONArray("link"));
                if (id.isEmpty()) id = url;
                if (!id.isEmpty() && !url.isEmpty() && isTrustedPostUrl(url)) {
                    posts.add(new Post(id, title, url, findCategory(entry.optJSONArray("category"))));
                }
            }
            return posts;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String findAlternateLink(JSONArray links) {
        if (links == null) return "";
        for (int i = 0; i < links.length(); i++) {
            JSONObject item = links.optJSONObject(i);
            if (item != null && "alternate".equals(item.optString("rel"))) {
                return item.optString("href", "");
            }
        }
        return "";
    }

    private String findCategory(JSONArray categories) {
        if (categories == null || categories.length() == 0) return "New article";
        JSONObject item = categories.optJSONObject(0);
        return item == null ? "New article" : item.optString("term", "New article");
    }

    private boolean isTrustedPostUrl(String value) {
        try {
            URL url = new URL(value);
            String host = url.getHost().toLowerCase();
            return "https".equalsIgnoreCase(url.getProtocol()) &&
                    (host.equals("jobalertz.com.ng") || host.equals("www.jobalertz.com.ng")
                            || host.endsWith(".jobalertz.com.ng"));
        } catch (Exception e) { return false; }
    }

    private void sendNotification(Post post) {
        Context context = getApplicationContext();
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(EXTRA_POST_URL, post.url);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int requestCode = Math.abs(post.id.hashCode());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, requestCode, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(post.title)
                .setContentText("New on JobAlertzNg - " + post.category)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "A new post has been published on JobAlertzNg. Tap to read: " + post.title))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(0xFF00834D);
        try {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build());
        } catch (SecurityException ignored) {
            // User has not granted notification permission.
        }
    }

    private static class Post {
        final String id, title, url, category;
        Post(String id, String title, String url, String category) {
            this.id = id; this.title = title; this.url = url; this.category = category;
        }
    }
}
