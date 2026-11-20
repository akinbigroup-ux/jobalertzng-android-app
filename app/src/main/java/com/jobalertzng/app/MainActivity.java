
package com.jobalertzng.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.ads.MobileAds;

import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String HOME = "https://www.jobalertz.com.ng/";
    private static final String CAREER = "https://www.jobalertz.com.ng/search/label/Career%20Advice";
    private static final String STORIES = "https://www.jobalertz.com.ng/p/beyond-office-series.html";
    private static final String EXPIRED = "https://www.jobalertz.com.ng/p/expired-jobs.html";
    private static final String CLOSING = "https://www.jobalertz.com.ng/p/deadline-approaching-vacancies.html";
    private static final String HOST = "www.jobalertz.com.ng";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4102;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView[] navLabels = new TextView[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(11, 35, 69));
        getWindow().setNavigationBarColor(Color.rgb(11, 35, 69));

        createNotificationChannel();
        buildInterface();
        configureWebView();
        schedulePostChecks();
        requestNotificationPermissionIfNeeded();

        String notificationUrl = getIntent() != null
                ? getIntent().getStringExtra(PostCheckWorker.EXTRA_POST_URL) : null;

        loadPage(
                isTrustedBlogUrl(notificationUrl) ? notificationUrl : HOME,
                "Home"
        );

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Exit JobAlertzNg?")
                                    .setMessage("Would you like to close the app?")
                                    .setNegativeButton("Stay",
                                            (d, w) -> d.dismiss())
                                    .setPositiveButton("Exit",
                                            (d, w) -> finish())
                                    .show();
                        }
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String url = intent.getStringExtra(PostCheckWorker.EXTRA_POST_URL);

        if (isTrustedBlogUrl(url) && webView != null) {
            loadPage(url, "Home");
        }
    }

    private void buildInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 35, 69));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(12), dp(10));
        header.setBackgroundColor(Color.rgb(11, 35, 69));

        // Official JobAlertzNg logo.
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.jobalertzng_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(dp(2), dp(2), dp(2), dp(2));
        logo.setContentDescription("JobAlertzNg official logo");

        header.addView(logo,
                new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText("JobAlertzNg");
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView tagline = new TextView(this);
        tagline.setText("Your next opportunity starts here.");
        tagline.setTextColor(Color.rgb(205, 218, 232));
        tagline.setTextSize(10);

        brand.addView(name);
        brand.addView(tagline);

        header.addView(brand,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                ));

        Button search = new Button(this);
        search.setText("SEARCH");
        search.setTextSize(10);
        search.setTextColor(Color.rgb(11, 35, 69));
        search.setAllCaps(true);
        search.setPadding(dp(8), 0, dp(8), 0);
        search.setMinWidth(0);
        search.setMinHeight(dp(36));
        search.setBackground(makeRounded(Color.rgb(255, 209, 42), 18));
        search.setOnClickListener(v -> showSearchDialog());

        header.addView(search,
                new LinearLayout.LayoutParams(dp(78), dp(36)));

        root.addView(header,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);
        progressBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(0, 131, 77)
                )
        );

        progressBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(231, 237, 243)
                )
        );

        root.addView(progressBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(3)
                ));

        webView = new WebView(this);

        root.addView(webView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));
        nav.setPadding(dp(3), dp(5), dp(3), dp(5));

        String[] icons = {"⌂", "▦", "✦", "▤", "•••"};
        String[] labels = {"Home", "Jobs", "Career", "Stories", "More"};

        for (int i = 0; i < labels.length; i++) {

            final int index = i;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(4));
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackground(makeRounded(Color.WHITE, 10));

            TextView icon = new TextView(this);
            icon.setText(icons[i]);
            icon.setTextSize(i == 4 ? 17 : 21);
            icon.setGravity(Gravity.CENTER);
            icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(2), 0, 0);

            item.addView(icon,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(25)
                    ));

            item.addView(label,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(17)
                    ));

            navLabels[i] = label;

            item.setOnClickListener(
                    v -> handleNavigation(index, item)
            );

            nav.addView(item,
                    new LinearLayout.LayoutParams(
                            0,
                            dp(51),
                            1
                    ));
        }

        root.addView(nav,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(60)
                ));

        setContentView(root);

        // Keep app content between Android system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {

            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );

            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );

            return windowInsets;
        });

        ViewCompat.requestApplyInsets(root);

        updateSelectedTab(0);
    }

    private void configureWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        // Allow third-party cookies needed by advertising services.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Allow HTML video playback without requiring a tap.
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Register this WebView with Google's WebView API for Ads.
        // This must run on the main Android thread.
        MobileAds.registerWebView(webView);

        webView.setBackgroundColor(Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return routeUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                return routeUrl(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView view, int progress) {

                progressBar.setProgress(progress);

                progressBar.setVisibility(
                        progress >= 100 ? View.GONE : View.VISIBLE
                );
            }
        });
    }

    private boolean routeUrl(Uri uri) {

        if (uri == null) return true;

        String scheme = uri.getScheme() == null
                ? "" : uri.getScheme().toLowerCase();

        String host = uri.getHost() == null
                ? "" : uri.getHost().toLowerCase();

        if ("https".equals(scheme)
                && (host.equals("jobalertz.com.ng")
                || host.equals(HOST)
                || host.endsWith(".jobalertz.com.ng"))) {
            return false;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(
                    this,
                    "No app found to open this link.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        return true;
    }

    private void handleNavigation(int index, View anchor) {

        switch (index) {
            case 0:
                loadPage(HOME, "Home");
                break;

            case 1:
                showJobsMenu(anchor);
                break;

            case 2:
                loadPage(CAREER, "Career");
                break;

            case 3:
                loadPage(STORIES, "Stories");
                break;

            case 4:
                showMoreMenu(anchor);
                break;
        }
    }

    private void showJobsMenu(View anchor) {

        updateSelectedTab(1);

        PopupMenu menu = new PopupMenu(this, anchor);

        menu.getMenu().add(0, 1, 0, "Lagos Jobs");
        menu.getMenu().add(0, 2, 1, "Abuja Jobs");
        menu.getMenu().add(0, 3, 2, "Other States");
        menu.getMenu().add(0, 4, 3, "Closing Soon");
        menu.getMenu().add(0, 5, 4, "Expired Jobs");

        menu.setOnMenuItemClickListener(item -> {

            switch (item.getItemId()) {

                case 1:
                    loadPage(labelUrl("Lagos"), "Jobs");
                    return true;

                case 2:
                    loadPage(labelUrl("Abuja"), "Jobs");
                    return true;

                case 3:
                    loadPage(labelUrl("Others"), "Jobs");
                    return true;

                case 4:
                    loadPage(CLOSING, "Jobs");
                    return true;

                case 5:
                    loadPage(EXPIRED, "Jobs");
                    return true;

                default:
                    return false;
            }
        });

        menu.show();
    }

    private void showMoreMenu(View anchor) {

        updateSelectedTab(4);

        PopupMenu menu = new PopupMenu(this, anchor);

        menu.getMenu().add(0, 1, 0, "Interview Experiences");
        menu.getMenu().add(0, 2, 1, "Job Stories");
        menu.getMenu().add(0, 3, 2, "About Us");
        menu.getMenu().add(0, 4, 3, "Contact Us");
        menu.getMenu().add(0, 5, 4, "Advertise a Job");
        menu.getMenu().add(0, 6, 5, "Privacy Policy");
        menu.getMenu().add(0, 7, 6, "Terms of Service");
        menu.getMenu().add(0, 8, 7, "Disclaimer");

        menu.setOnMenuItemClickListener(item -> {

            switch (item.getItemId()) {

                case 1:
                    loadPage(labelUrl("Interview Experiences"), "More");
                    return true;

                case 2:
                    loadPage(labelUrl("Job Stories"), "More");
                    return true;

                case 3:
                    loadPage(HOME + "p/about-us.html", "More");
                    return true;

                case 4:
                    loadPage(HOME + "p/contact-us.html", "More");
                    return true;

                case 5:
                    startActivity(new Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:info@jobalertz.com.ng?subject=Job%20Advertisement%20Enquiry")
                    ));
                    return true;

                case 6:
                    loadPage(HOME + "p/privacy-policy.html", "More");
                    return true;

                case 7:
                    loadPage(HOME + "p/terms-of-service.html", "More");
                    return true;

                case 8:
                    loadPage(HOME + "p/disclaimer.html", "More");
                    return true;

                default:
                    return false;
            }
        });

        menu.show();
    }

    private String labelUrl(String label) {

        try {
            return HOME + "search/label/"
                    + URLEncoder.encode(label, "UTF-8").replace("+", "%20");

        } catch (Exception e) {
            return HOME;
        }
    }

    private void loadPage(String url, String tab) {

        if (!isTrustedBlogUrl(url)) {
            url = HOME;
        }

        int selected = "Home".equals(tab) ? 0
                : "Jobs".equals(tab) ? 1
                : "Career".equals(tab) ? 2
                : "Stories".equals(tab) ? 3 : 4;

        updateSelectedTab(selected);
        webView.loadUrl(url);
    }

    private void updateSelectedTab(int selected) {

        int green = Color.rgb(0, 131, 77);

        for (int i = 0; i < navLabels.length; i++) {

            if (navLabels[i] != null) {

                navLabels[i].setTextColor(
                        i == selected
                                ? green
                                : Color.rgb(117, 129, 145)
                );

                View parent = (View) navLabels[i].getParent();

                if (parent != null) {
                    parent.setBackground(makeRounded(
                            i == selected
                                    ? Color.rgb(235, 246, 239)
                                    : Color.WHITE,
                            10
                    ));
                }
            }
        }
    }

    private void showSearchDialog() {

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Job title, keyword or location");
        input.setPadding(dp(14), dp(10), dp(14), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("Search JobAlertzNg")
                .setView(input)
                .setNegativeButton("Cancel",
                        (d, w) -> d.dismiss())
                .setPositiveButton("Search", (d, w) -> {

                    String query = input.getText().toString().trim();

                    if (!query.isEmpty()) {

                        try {
                            loadPage(
                                    HOME + "?q="
                                            + URLEncoder.encode(query, "UTF-8"),
                                    "Home"
                            );

                        } catch (Exception ignored) {
                            loadPage(HOME, "Home");
                        }
                    }
                })
                .show();
    }

    private void schedulePostChecks() {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest baseline =
                new OneTimeWorkRequest.Builder(PostCheckWorker.class)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "janz-post-baseline",
                ExistingWorkPolicy.KEEP,
                baseline
        );

        PeriodicWorkRequest periodic =
                new PeriodicWorkRequest.Builder(
                        PostCheckWorker.class,
                        15,
                        TimeUnit.MINUTES
                )
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "janz-post-check",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
        );
    }

    private void createNotificationChannel() {

        NotificationChannelCompat channel =
                new NotificationChannelCompat.Builder(
                        PostCheckWorker.CHANNEL_ID,
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                )
                        .setName("New JobAlertzNg posts")
                        .setDescription(
                                "New job vacancies, career advice and articles."
                        )
                        .build();

        NotificationManagerCompat.from(this)
                .createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33
                && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private boolean isTrustedBlogUrl(String value) {

        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        try {

            Uri uri = Uri.parse(value);

            String host = uri.getHost() == null
                    ? "" : uri.getHost().toLowerCase();

            return "https".equalsIgnoreCase(uri.getScheme())
                    && (
                            host.equals("jobalertz.com.ng")
                            || host.equals(HOST)
                            || host.endsWith(".jobalertz.com.ng")
                    );

        } catch (Exception e) {
            return false;
        }
    }

    private android.graphics.drawable.GradientDrawable makeRounded(
            int color,
            int radius
    ) {

        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
