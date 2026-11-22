
package com.jobalertzng.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class JobAlertzFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "JA_FCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        // Register the refreshed token with Firebase.
        FirebaseTokenManager.register();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();

        String title = data.get("title");
        String body = data.get("body");
        String link = data.get("link");
        String postId = data.get("postId");

        if (title == null || title.trim().isEmpty()) {
            title = "JobAlertzNg";
        }

        if (body == null || body.trim().isEmpty()) {
            body = "A new job opportunity or career update is available.";
        }

        showNotification(title, body, link, postId);
    }

    private void showNotification(
            String title,
            String body,
            String link,
            String postId
    ) {
        createNotificationChannelIfNeeded();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        String postUrl = extractTrustedPostUrl(link);

        if (postUrl != null) {
            intent.putExtra(PostCheckWorker.EXTRA_POST_URL, postUrl);
        }

        int requestCode = postId != null
                ? postId.hashCode()
                : (link != null ? link.hashCode() : (int) System.currentTimeMillis());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        PostCheckWorker.CHANNEL_ID
                )
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(body)
                        )
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setColor(0xFF00834D);

        int notificationId = requestCode;

        try {
            NotificationManagerCompat.from(this)
                    .notify(notificationId, builder.build());
        } catch (SecurityException error) {
            android.util.Log.e(TAG, "Notification permission not granted", error);
        }
    }

    private String extractTrustedPostUrl(String link) {

        if (link == null || link.trim().isEmpty()) {
            return null;
        }

        try {
            Uri uri = Uri.parse(link);

            // The server may send a Firebase Hosting URL containing
            // the original Blogger post URL in the "post" parameter.
            String postUrl = uri.getQueryParameter("post");

            if (isTrustedBlogUrl(postUrl)) {
                return postUrl;
            }

            // Also accept a direct trusted Blogger post URL.
            if (isTrustedBlogUrl(link)) {
                return link;
            }

        } catch (Exception error) {
            android.util.Log.e(TAG, "Unable to read notification link", error);
        }

        return null;
    }

    private boolean isTrustedBlogUrl(String url) {

        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            Uri uri = Uri.parse(url);

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            String host = uri.getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase();

            return host.equals("jobalertz.com.ng")
                    || host.equals("www.jobalertz.com.ng")
                    || host.endsWith(".jobalertz.com.ng");

        } catch (Exception error) {
            return false;
        }
    }

    private void createNotificationChannelIfNeeded() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    PostCheckWorker.CHANNEL_ID,
                    "New JobAlertzNg posts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setDescription(
                    "New job vacancies, career advice and articles."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
