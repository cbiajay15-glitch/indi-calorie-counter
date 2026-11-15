package com.indicaloriecounter.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Indi Calorie Counter: the web app bundled in assets/www, shown full screen in a WebView.
 * Logged data stays in this app's WebView storage on the phone. The only network use is the
 * web page downloading the latest food list (plain data) from the app's GitHub Pages site.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final String START_URL = "file:///android_asset/www/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecorder();
        SharedPreferences crash = getSharedPreferences("crash", MODE_PRIVATE);
        String last = crash.getString("last", null);
        if (last != null) {
            crash.edit().remove("last").commit();
            showProblem("Pichhli baar app band ho gayi thi", last);
            return;
        }
        try {
            start(savedInstanceState);
        } catch (Throwable t) {
            showProblem("App khul nahi paayi", Log.getStackTraceString(t));
        }
    }

    private void start(Bundle savedInstanceState) {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = getResources().getColor(R.color.bg, getTheme());
        try { styleSystemBars(night); } catch (Throwable ignored) { /* cosmetic only */ }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        // Keep content clear of the status bar, navigation bar and keyboard (Android 15 draws edge to edge).
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
                } else {
                    top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
                }
                v.setPadding(left, top, right, bottom);
                return insets;
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(bg);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "IndiAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // The page engine crashed: rebuild the screen instead of letting the whole app close.
                if (web != null) { web.destroy(); web = null; }
                recreate();
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                // Any outside link opens in the phone's browser, never inside the app.
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                // Any file type: many phones label .json backups as "unknown", which a JSON-only filter would hide.
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose backup file"), PICK_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    /** Saves the error of an unexpected crash so the next launch can show it instead of closing silently. */
    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    getSharedPreferences("crash", MODE_PRIVATE).edit()
                            .putString("last", Log.getStackTraceString(error)).commit();
                } catch (Throwable ignored) { }
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    /** A plain native screen (no WebView) that explains the problem and shows the error text. */
    private void showProblem(String title, String details) {
        boolean noWebView = details != null && (details.contains("WebView") || details.contains("webview")
                || details.contains("MissingWebViewPackage"));
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad * 2, pad, pad);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(22);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(head);

        TextView help = new TextView(this);
        help.setTextSize(16);
        help.setPadding(0, pad / 2, 0, pad / 2);
        help.setText(noWebView
                ? "Phone mein \"Android System WebView\" nahi mila ya band hai. Play Store kholo, \"Android System WebView\" aur \"Chrome\" search karke Update / Enable karo, phir app dobara kholo."
                : "Is screen ka screenshot le kar bhejo, isse problem theek ho jaayegi. Aapka saved data surakshit hai.");
        box.addView(help);

        Button retry = new Button(this);
        retry.setText("Dobara kholo");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { recreate(); }
        });
        box.addView(retry);

        TextView err = new TextView(this);
        err.setTextSize(12);
        err.setTextIsSelectable(true);
        err.setPadding(0, pad / 2, 0, 0);
        String info = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n";
        err.setText(info + (details == null ? "" : (details.length() > 3000 ? details.substring(0, 3000) : details)));
        box.addView(err);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);
    }

    private void styleSystemBars(boolean night) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else if (!night) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web == null) { finish(); return; }
        // Let the page close its food sheet or return to Today first; close the app only when it says so.
        web.evaluateJavascript("window.__indiBack ? window.__indiBack() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Called from the page's Settings > Export backup. Returns where the file went, or "" on failure. */
    public class Bridge {
        @JavascriptInterface
        public String saveBackup(String fileName, String json) {
            String name = fileName == null ? "" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (name.isEmpty() || name.length() > 100) name = "indi-calorie-counter-backup.json";
            byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "";
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    if (out == null) return "";
                    try { out.write(bytes); } finally { out.close(); }
                    return "Downloads";
                }
                // Android 7-9: the app's own Downloads folder needs no storage permission.
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return "";
                File f = new File(dir, name);
                FileOutputStream out = new FileOutputStream(f);
                try { out.write(bytes); } finally { out.close(); }
                return f.getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }
    }
}
