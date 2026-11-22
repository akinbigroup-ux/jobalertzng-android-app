
package com.jobalertzng.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String HOME =
            "https://www.jobalertz.com.ng/";

    private static final String CAREER =
            "https://www.jobalertz.com.ng/search/label/Career%20Advice";

    private static final String STORIES =
            "https://www.jobalertz.com.ng/p/beyond-office-series.html";

    private static final String EXPIRED =
            "https://www.jobalertz.com.ng/p/expired-jobs.html";

    private static final String CLOSING =
            "https://www.jobalertz.com.ng/p/deadline-approaching-vacancies.html";

    private static final String HOST = "www.jobalertz.com.ng";

    private static final int NOTIFICATION_PERMISSION_REQUEST = 4102;

    private WebView webView;
    private ProgressBar progressBar;
    private PullToRefreshFrameLayout contentFrame;
    private LinearLayout savedScreen;
    private LinearLayout savedList;

    private TextView[] navLabels = new TextView[6];

    private boolean savedScreenVisible = false;
    private int lastWebTabIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(11, 35, 69));
        getWindow().setNavigationBarColor(Color.rgb(11, 35, 69));

        createNotificationChannel();

        // Register this device with Firebase for push notifications.
        FirebaseTokenManager.register();

        buildInterface();
        configureWebView();
        schedulePostChecks();
        requestNotificationPermissionIfNeeded();

        String notificationUrl = getIntent() != null
                ? getIntent().getStringExtra(PostCheckWorker.EXTRA_POST_URL)
                : null;

        loadPage(
                isTrustedBlogUrl(notificationUrl) ? notificationUrl : HOME,
                "Home"
        );

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {

                        if (savedScreenVisible) {
                            showWebContent();
                            updateSelectedTab(lastWebTabIndex);
                            return;
                        }

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

        contentFrame = new PullToRefreshFrameLayout();

        webView = new WebView(this);

        contentFrame.addView(webView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

        savedScreen = buildSavedScreen();

        contentFrame.addView(savedScreen,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

        savedScreen.setVisibility(View.GONE);

        root.addView(contentFrame,
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
        nav.setPadding(dp(2), dp(5), dp(2), dp(5));

        String[] icons = {"⌂", "▦", "✦", "▤", "♡", "•••"};

        String[] labels = {
                "Home", "Jobs", "Career", "Stories", "Saved", "More"
        };

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
            icon.setTextSize(i == 5 ? 17 : 21);
            icon.setGravity(Gravity.CENTER);
            icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(9);
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

    // Pull-to-refresh container.
    private class PullToRefreshFrameLayout extends FrameLayout {

        private float startX;
        private float startY;
        private final int touchSlop;
        private final int refreshDistance;
        private boolean refreshing = false;

        public PullToRefreshFrameLayout() {
            super(MainActivity.this);

            touchSlop = ViewConfiguration.get(MainActivity.this)
                    .getScaledTouchSlop();

            refreshDistance = dp(90);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    break;

                case MotionEvent.ACTION_MOVE:

                    if (refreshing || savedScreenVisible || webView == null) {
                        return false;
                    }

                    float distanceY = event.getY() - startY;
                    float distanceX = event.getX() - startX;

                    if (webView.getScrollY() <= 0
                            && distanceY > touchSlop
                            && distanceY > Math.abs(distanceX)) {
                        return true;
                    }

                    break;
            }

            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_MOVE:
                    return true;

                case MotionEvent.ACTION_UP:

                    float pullDistance = event.getY() - startY;

                    if (!refreshing && pullDistance >= refreshDistance) {
                        refreshCurrentPage();
                    }

                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }

            return true;
        }

        private void refreshCurrentPage() {

            if (refreshing || webView == null) {
                return;
            }

            refreshing = true;

            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(25);

            String currentUrl = webView.getUrl();

            if (isTrustedBlogUrl(currentUrl)) {
                webView.reload();
            } else {
                loadPage(HOME, "Home");
            }

            Toast.makeText(
                    MainActivity.this,
                    "Refreshing...",
                    Toast.LENGTH_SHORT
            ).show();
        }

        public void finishRefresh() {
            refreshing = false;
        }
    }

    private LinearLayout buildSavedScreen() {

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(245, 248, 251));
        screen.setPadding(dp(16), dp(18), dp(16), dp(12));

        TextView heading = new TextView(this);
        heading.setText("Saved Jobs");
        heading.setTextColor(Color.rgb(16, 45, 86));
        heading.setTextSize(23);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        screen.addView(heading);

        TextView description = new TextView(this);
        description.setText(
                "Your bookmarked opportunities, saved on this device."
        );
        description.setTextColor(Color.rgb(95, 108, 124));
        description.setTextSize(12);
        description.setPadding(0, dp(5), 0, dp(14));

        screen.addView(description);

        ScrollView scrollView = new ScrollView(this);

        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(savedList);

        screen.addView(scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

        return screen;
    }

    private void refreshSavedJobs() {

        if (savedList == null) return;

        savedList.removeAllViews();

        List<SavedJobStore.SavedJob> jobs =
                SavedJobStore.getAll(this);

        if (jobs.isEmpty()) {

            TextView empty = new TextView(this);
            empty.setText(
                    "You haven't saved any jobs yet.\n\n"
                    + "Open a vacancy and tap the Save Job button "
                    + "to keep it here."
            );
            empty.setTextColor(Color.rgb(90, 105, 122));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(45), dp(20), dp(45));
            empty.setBackground(makeRounded(Color.WHITE, 12));

            savedList.addView(empty);
            return;
        }

        for (SavedJobStore.SavedJob job : jobs) {

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(makeRounded(Color.WHITE, 12));

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            cardParams.bottomMargin = dp(10);

            TextView title = new TextView(this);
            title.setText(job.title);
            title.setTextColor(Color.rgb(16, 45, 86));
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            card.addView(title);

            TextView address = new TextView(this);
            address.setText(job.url);
            address.setTextColor(Color.rgb(120, 130, 145));
            address.setTextSize(10);
            address.setPadding(0, dp(5), 0, dp(10));

            card.addView(address);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            TextView open = new TextView(this);
            open.setText("OPEN JOB");
            open.setTextColor(Color.WHITE);
            open.setTextSize(11);
            open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            open.setGravity(Gravity.CENTER);
            open.setPadding(dp(16), dp(9), dp(16), dp(9));
            open.setBackground(makeRounded(Color.rgb(0, 131, 77), 8));

            open.setOnClickListener(v -> {
                loadPage(job.url, "Home");
            });

            TextView remove = new TextView(this);
            remove.setText("REMOVE");
            remove.setTextColor(Color.rgb(180, 55, 55));
            remove.setTextSize(11);
            remove.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(16), dp(9), dp(16), dp(9));
            remove.setBackground(makeRounded(Color.rgb(253, 238, 238), 8));

            LinearLayout.LayoutParams removeParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            removeParams.leftMargin = dp(10);

            remove.setOnClickListener(v -> {

                SavedJobStore.remove(this, job.url);
                refreshSavedJobs();

                Toast.makeText(
                        this,
                        "Job removed from Saved Jobs.",
                        Toast.LENGTH_SHORT
                ).show();
            });

            actions.addView(open);
            actions.addView(remove, removeParams);

            card.addView(actions);

            savedList.addView(card, cardParams);
        }
    }

    private void showSavedScreen() {

        lastWebTabIndex = getSelectedWebTab();
        savedScreenVisible = true;

        webView.setVisibility(View.GONE);
        savedScreen.setVisibility(View.VISIBLE);

        progressBar.setVisibility(View.GONE);

        refreshSavedJobs();
        updateSelectedTab(4);
    }

    private void showWebContent() {

        savedScreenVisible = false;

        savedScreen.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private int getSelectedWebTab() {
        return lastWebTabIndex;
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

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        settings.setMediaPlaybackRequiresUserGesture(false);

        MobileAds.registerWebView(webView);

        webView.addJavascriptInterface(
                new SavedJobsBridge(),
                "AndroidSavedJobs"
        );

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

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                contentFrame.finishRefresh();
                injectSaveJobButton();
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

    private void injectSaveJobButton() {

        if (webView == null || !isJobPostUrl(webView.getUrl())) {
            return;
        }

        String script =
                "(function(){"
                + "if(document.getElementById('janz-native-actions'))return;"

                + "var box=document.createElement('div');"
                + "box.id='janz-native-actions';"
                + "box.style.cssText='position:fixed;right:12px;bottom:18px;"
                + "z-index:2147483647;display:flex;gap:8px;"
                + "align-items:center;';"

                + "var save=document.createElement('button');"
                + "save.id='janz-native-save-job';"
                + "save.type='button';"
                + "save.style.cssText='border:0;border-radius:30px;"
                + "padding:12px 15px;background:#00834D;color:#fff;"
                + "font-size:13px;font-weight:700;"
                + "box-shadow:0 3px 12px rgba(0,0,0,.25);';"

                + "var share=document.createElement('button');"
                + "share.id='janz-native-share-job';"
                + "share.type='button';"
                + "share.innerText='Share ↗';"
                + "share.style.cssText='border:0;border-radius:30px;"
                + "padding:12px 15px;background:#102D56;color:#fff;"
                + "font-size:13px;font-weight:700;"
                + "box-shadow:0 3px 12px rgba(0,0,0,.25);';"

                + "var title=document.querySelector('h1.post-title, "
                + "h1.entry-title, .post-title, h1');"
                + "var jobTitle=title?title.innerText.trim():document.title;"

                + "function update(){"
                + "try{save.innerText=AndroidSavedJobs.isSaved(location.href)"
                + "?'Saved ✓':'Save Job ♡';}"
                + "catch(e){save.innerText='Save Job ♡';}"
                + "}"

                + "save.onclick=function(){"
                + "try{"
                + "var state=AndroidSavedJobs.toggleJob(jobTitle,location.href);"
                + "save.innerText=state?'Saved ✓':'Save Job ♡';"
                + "}catch(e){save.innerText='Unable to save';}"
                + "};"

                + "share.onclick=function(){"
                + "try{AndroidSavedJobs.shareJob(jobTitle,location.href);}"
                + "catch(e){share.innerText='Unable to share';}"
                + "};"

                + "box.appendChild(save);"
                + "box.appendChild(share);"
                + "document.body.appendChild(box);"
                + "update();"
                + "})();";

        webView.evaluateJavascript(script, null);
    }

    private boolean isJobPostUrl(String value) {

        if (!isTrustedBlogUrl(value)) return false;

        try {
            String path = Uri.parse(value).getPath();

            return path != null
                    && path.matches("/[0-9]{4}/[0-9]{1,2}/.+\\.html");

        } catch (Exception e) {
            return false;
        }
    }

    private class SavedJobsBridge {

        @JavascriptInterface
        public boolean isSaved(String url) {

            if (!isJobPostUrl(url)) return false;

            return SavedJobStore.isSaved(
                    MainActivity.this,
                    url
            );
        }

        @JavascriptInterface
        public boolean toggleJob(String title, String url) {

            if (!isJobPostUrl(url)) return false;

            boolean saved;

            if (SavedJobStore.isSaved(MainActivity.this, url)) {

                SavedJobStore.remove(MainActivity.this, url);
                saved = false;

            } else {

                saved = SavedJobStore.save(
                        MainActivity.this,
                        title,
                        url
                );
            }

            final boolean result = saved;

            runOnUiThread(() -> {

                Toast.makeText(
                        MainActivity.this,
                        result
                                ? "Job saved successfully."
                                : "Unable to save this job.",
                        Toast.LENGTH_SHORT
                ).show();

                if (savedScreenVisible) {
                    refreshSavedJobs();
                }
            });

            return saved;
        }

        @JavascriptInterface
        public void shareJob(String title, String url) {

            if (!isJobPostUrl(url)) {
                return;
            }

            runOnUiThread(() ->
                    NativeShareHelper.share(
                            MainActivity.this,
                            title,
                            url
                    )
            );
        }
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
                showSavedScreen();
                break;

            case 5:
                showMoreMenu(anchor);
                break;
        }
    }

    // Premium Jobs menu.
    private void showJobsMenu(View anchor) {

        updateSelectedTab(1);

        showPremiumMenu(
                "Explore Jobs",
                "Find opportunities across Nigeria",
                new String[]{
                        "Lagos Jobs",
                        "Abuja Jobs",
                        "Other States",
                        "Closing Soon",
                        "Expired Jobs"
                },
                new String[]{"⌖", "⌖", "◎", "◷", "✓"},
                new Runnable[]{
                        () -> loadPage(labelUrl("Lagos"), "Jobs"),
                        () -> loadPage(labelUrl("Abuja"), "Jobs"),
                        () -> loadPage(labelUrl("Others"), "Jobs"),
                        () -> loadPage(CLOSING, "Jobs"),
                        () -> loadPage(EXPIRED, "Jobs")
                }
        );
    }

    // Premium More menu.
    private void showMoreMenu(View anchor) {

        updateSelectedTab(5);

        showPremiumMenu(
                "More from JobAlertzNg",
                "More resources to support your career",
                new String[]{
                        "Interview Experiences",
                        "Job Stories",
                        "About Us",
                        "Contact Us",
                        "Advertise a Job",
                        "Privacy Policy",
                        "Terms of Service",
                        "Disclaimer"
                },
                new String[]{"▤", "✦", "ⓘ", "✉", "＋", "▣", "≡", "✓"},
                new Runnable[]{
                        () -> loadPage(labelUrl("Interview Experiences"), "More"),
                        () -> loadPage(labelUrl("Job Stories"), "More"),
                        () -> loadPage(HOME + "p/about-us.html", "More"),
                        () -> loadPage(HOME + "p/contact-us.html", "More"),
                        () -> {
                            try {
                                startActivity(new Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:info@jobalertz.com.ng?subject=Job%20Advertisement%20Enquiry")
                                ));
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(
                                        this,
                                        "No email app is available.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        },
                        () -> loadPage(HOME + "p/privacy-policy.html", "More"),
                        () -> loadPage(HOME + "p/terms-of-service.html", "More"),
                        () -> loadPage(HOME + "p/disclaimer.html", "More")
                }
        );
    }

    // Custom animated premium bottom menu.
    private void showPremiumMenu(
            String heading,
            String subtitle,
            String[] itemNames,
            String[] symbols,
            Runnable[] actions
    ) {

        Dialog dialog = new Dialog(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(18));
        panel.setBackground(makeRounded(Color.WHITE, 24));
        panel.setElevation(dp(16));

        // Drag handle.
        TextView handle = new TextView(this);
        handle.setBackground(makeRounded(
                Color.rgb(190, 201, 215), 20
        ));

        LinearLayout.LayoutParams handleParams =
                new LinearLayout.LayoutParams(dp(42), dp(5));

        handleParams.gravity = Gravity.CENTER;
        handleParams.bottomMargin = dp(17);

        panel.addView(handle, handleParams);

        // Heading.
        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(Color.rgb(11, 35, 69));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        panel.addView(title);

        // Subtitle.
        TextView description = new TextView(this);
        description.setText(subtitle);
        description.setTextColor(Color.rgb(112, 125, 143));
        description.setTextSize(12);
        description.setPadding(0, dp(4), 0, dp(17));

        panel.addView(description);

        // Scrollable menu options.
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout menuItems = new LinearLayout(this);
        menuItems.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < itemNames.length; i++) {

            final int position = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackground(makeRounded(
                    Color.rgb(246, 249, 252), 14
            ));
            row.setClickable(true);
            row.setFocusable(true);
            row.setElevation(dp(1));

            // Circular navy icon.
            TextView icon = new TextView(this);
            icon.setText(symbols[i]);
            icon.setTextColor(Color.WHITE);
            icon.setTextSize(19);
            icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(makeRounded(
                    Color.rgb(16, 45, 86), 30
            ));

            row.addView(icon,
                    new LinearLayout.LayoutParams(dp(40), dp(40)));

            // Menu option text.
            TextView name = new TextView(this);
            name.setText(itemNames[i]);
            name.setTextColor(Color.rgb(25, 43, 67));
            name.setTextSize(14);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setPadding(dp(13), 0, dp(5), 0);

            row.addView(name,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    ));

            // Right arrow.
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(Color.rgb(0, 131, 77));
            arrow.setTextSize(27);
            arrow.setGravity(Gravity.CENTER);

            row.addView(arrow,
                    new LinearLayout.LayoutParams(dp(25), dp(40)));

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(56)
                    );

            rowParams.bottomMargin = dp(8);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                actions[position].run();
            });

            menuItems.addView(row, rowParams);
        }

        scrollView.addView(menuItems);

        int scrollHeight = Math.min(420, itemNames.length * 64);

        panel.addView(scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(scrollHeight)
                ));

        // Close button.
        TextView close = new TextView(this);
        close.setText("×    Close Menu");
        close.setTextColor(Color.rgb(16, 45, 86));
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(makeRounded(
                Color.rgb(235, 241, 249), 14
        ));
        close.setPadding(0, dp(13), 0, dp(13));

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        closeParams.topMargin = dp(10);

        close.setOnClickListener(v -> dialog.dismiss());

        panel.addView(close, closeParams);

        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(
                    android.R.color.transparent
            );

            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.BOTTOM);

            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;

            window.setAttributes(params);
        }

        // Start slightly below its final position.
        panel.setTranslationY(dp(90));
        panel.setAlpha(0.85f);

        dialog.show();

        // Set window size after showing the dialog.
        if (dialog.getWindow() != null) {

            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );

            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }

        // Smooth slide-up animation.
        panel.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
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

        showWebContent();

        int selected = "Home".equals(tab) ? 0
                : "Jobs".equals(tab) ? 1
                : "Career".equals(tab) ? 2
                : "Stories".equals(tab) ? 3
                : "More".equals(tab) ? 5 : 0;

        lastWebTabIndex = selected;

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
