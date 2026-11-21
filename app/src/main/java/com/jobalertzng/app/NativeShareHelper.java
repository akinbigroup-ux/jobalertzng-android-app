
package com.jobalertzng.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class NativeShareHelper {

    private NativeShareHelper() {
    }

    public static void share(
            Context context,
            String title,
            String url
    ) {

        if (context == null || url == null || url.trim().isEmpty()) {
            return;
        }

        Uri uri = Uri.parse(url.trim());

        String host = uri.getHost() == null
                ? ""
                : uri.getHost().toLowerCase();

        boolean trusted =
                "https".equalsIgnoreCase(uri.getScheme())
                && (
                        host.equals("jobalertz.com.ng")
                        || host.equals("www.jobalertz.com.ng")
                        || host.endsWith(".jobalertz.com.ng")
                );

        if (!trusted) {
            return;
        }

        String jobTitle = title == null || title.trim().isEmpty()
                ? "Job opportunity"
                : title.trim();

        String message = jobTitle
                + "\n\nView this opportunity on JobAlertzNg:\n"
                + uri.toString();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, jobTitle);
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);

        context.startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Share opportunity"
                )
        );
    }
}
