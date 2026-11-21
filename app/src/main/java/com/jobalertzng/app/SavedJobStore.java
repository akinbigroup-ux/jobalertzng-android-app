
package com.jobalertzng.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SavedJobStore {

    private static final String PREFS_NAME = "janz_saved_jobs";
    private static final String SAVED_JOBS_KEY = "saved_jobs";

    private SavedJobStore() {
    }

    public static final class SavedJob {
        public final String title;
        public final String url;
        public final long savedAt;

        public SavedJob(String title, String url, long savedAt) {
            this.title = title;
            this.url = url;
            this.savedAt = savedAt;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized boolean save(
            Context context,
            String title,
            String url
    ) {
        if (context == null || title == null || url == null) {
            return false;
        }

        title = title.trim();
        url = url.trim();

        if (title.isEmpty() || !isTrustedUrl(url)) {
            return false;
        }

        List<SavedJob> jobs = getAll(context);

        for (SavedJob job : jobs) {
            if (job.url.equals(url)) {
                return true;
            }
        }

        jobs.add(0, new SavedJob(
                title,
                url,
                System.currentTimeMillis()
        ));

        return writeAll(context, jobs);
    }

    public static synchronized List<SavedJob> getAll(Context context) {

        List<SavedJob> jobs = new ArrayList<>();

        if (context == null) {
            return jobs;
        }

        String stored = prefs(context).getString(
                SAVED_JOBS_KEY,
                "[]"
        );

        try {
            JSONArray array = new JSONArray(stored);

            for (int i = 0; i < array.length(); i++) {

                JSONObject item = array.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                String title = item.optString("title", "");
                String url = item.optString("url", "");
                long savedAt = item.optLong("savedAt", 0);

                if (!title.isEmpty() && isTrustedUrl(url)) {
                    jobs.add(new SavedJob(title, url, savedAt));
                }
            }

        } catch (Exception ignored) {
            // Ignore invalid stored entries.
        }

        return jobs;
    }

    public static synchronized boolean remove(
            Context context,
            String url
    ) {
        if (context == null || url == null) {
            return false;
        }

        List<SavedJob> jobs = getAll(context);
        boolean removed = jobs.removeIf(job -> job.url.equals(url));

        return !removed || writeAll(context, jobs);
    }

    public static synchronized boolean isSaved(
            Context context,
            String url
    ) {
        if (context == null || url == null) {
            return false;
        }

        for (SavedJob job : getAll(context)) {
            if (job.url.equals(url)) {
                return true;
            }
        }

        return false;
    }

    private static boolean writeAll(
            Context context,
            List<SavedJob> jobs
    ) {
        try {
            JSONArray array = new JSONArray();

            for (SavedJob job : jobs) {
                JSONObject item = new JSONObject();

                item.put("title", job.title);
                item.put("url", job.url);
                item.put("savedAt", job.savedAt);

                array.put(item);
            }

            return prefs(context).edit()
                    .putString(SAVED_JOBS_KEY, array.toString())
                    .commit();

        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isTrustedUrl(String value) {

        try {
            Uri uri = Uri.parse(value);

            String host = uri.getHost() == null
                    ? "" : uri.getHost().toLowerCase();

            return "https".equalsIgnoreCase(uri.getScheme())
                    && (
                            host.equals("jobalertz.com.ng")
                            || host.equals("www.jobalertz.com.ng")
                            || host.endsWith(".jobalertz.com.ng")
                    );

        } catch (Exception ignored) {
            return false;
        }
    }
}
