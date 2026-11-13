package io.github.jwillhub.rollingcredits;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import com.getcapacitor.WebViewListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RCPlayerPlugin.class);   // the page opens the native player through this
        registerPlugin(RCUpdatePlugin.class);   // and installs app updates through this
        registerPlugin(RCFilesPlugin.class);    // and saves backups through this
        installCrashRecorder();
        super.onCreate(savedInstanceState);

        // The screens run in a separate WebView process. If Android closes that process to free memory
        // (easy to hit while a big 4K file is playing), the default reaction is to crash the whole app.
        // Instead: note it, throw the dead view away and rebuild the screens.
        try {
            getBridge().addWebViewListener(new WebViewListener() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    boolean crashed = Build.VERSION.SDK_INT >= 26 && detail != null && detail.didCrash();
                    note("Screens process " + (crashed ? "crashed" : "was closed by Android to free memory") + " (recovered)");
                    try {
                        ViewGroup parent = (ViewGroup) view.getParent();
                        if (parent != null) parent.removeView(view);
                        view.destroy();
                    } catch (Throwable ignored) {}
                    getWindow().getDecorView().post(MainActivity.this::recreate);
                    return true;
                }
            });
        } catch (Throwable ignored) {}
        reportLastExit();

        // Posters and backdrops: once the phone has one, show it from storage instead of downloading it again.
        // (Only images and fonts go through this cache; the app's own data calls go through the native HTTP layer.)
        try { getBridge().getWebView().getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK); } catch (Exception ignored) {}

        // Android's back swipe / back button: step back inside the app (close the open card, leave a page,
        // then return to Discover) before leaving the app, the same way the browser's Back works.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView web = getBridge() != null ? getBridge().getWebView() : null;
                if (web == null) { leave(); return; }
                if (web.canGoBack()) { web.goBack(); return; }
                web.evaluateJavascript("window.rcBack ? String(window.rcBack()) : 'false'", result -> {
                    if (!"\"true\"".equals(result)) leave();
                });
            }
            private void leave() {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    // ---- Crash notes: what made the app close last time, shown once on the next start with a Copy button ----
    private static boolean recorderOn = false;
    private SharedPreferences diag() { return getSharedPreferences("diag", Context.MODE_PRIVATE); }
    private String stamp() { return new SimpleDateFormat("MMM d h:mm:ss a", Locale.US).format(new Date()); }

    private void note(String text) {
        try { diag().edit().putString("pending", diag().getString("pending", "") + stamp() + "  " + text + "\n").commit(); } catch (Throwable ignored) {}
    }

    private void installCrashRecorder() {
        if (recorderOn) return;
        recorderOn = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        final Context app = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
                String trace = sw.toString(); if (trace.length() > 6000) trace = trace.substring(0, 6000);
                SharedPreferences d = app.getSharedPreferences("diag", Context.MODE_PRIVATE);
                d.edit().putString("pending", d.getString("pending", "") + stamp() + "  App error on " + t.getName() + ":\n" + trace + "\n").commit();
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private static String reasonName(int r) {
        switch (r) {
            case 2: return "Low memory (Android closed it)";
            case 4: return "App error";
            case 5: return "Native crash";
            case 6: return "Not responding (ANR)";
            case 7: return "Failed to start";
            case 9: return "Used too many resources";
            case 3: return "Killed by signal";
            case 10: return "Killed by user";
            case 13: return "Killed by Android";
            case 1: return "Closed itself";
            case 11: return "Force stopped";
            case 12: return "Part it depends on stopped";
            case 14: return "App updated";
            case 8: return "Permission changed";
            case 0: return "Unknown";
            default: return "Exit reason " + r;
        }
    }

    private void reportLastExit() {
        StringBuilder msg = new StringBuilder();
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(getPackageName(), 0, 10);
                long seen = diag().getLong("seenExit", 0), newest = seen;
                int shown = 0;
                // every close since the last report (an app update or a swipe-away in between no longer hides a crash)
                for (ApplicationExitInfo x : list) {
                    if (x.getTimestamp() <= seen) continue;
                    newest = Math.max(newest, x.getTimestamp());
                    int r = x.getReason();
                    boolean bad = r == ApplicationExitInfo.REASON_CRASH || r == ApplicationExitInfo.REASON_CRASH_NATIVE
                        || r == ApplicationExitInfo.REASON_ANR || r == ApplicationExitInfo.REASON_LOW_MEMORY
                        || r == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE || r == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                        || r == ApplicationExitInfo.REASON_SIGNALED;
                    if (!bad || shown >= 3) continue;
                    shown++;
                    msg.append(new SimpleDateFormat("MMM d h:mm:ss a", Locale.US).format(new Date(x.getTimestamp())))
                       .append("  ").append(reasonName(r));
                    if (x.getDescription() != null) msg.append(": ").append(x.getDescription());
                    msg.append("\nMemory: ").append(x.getPss() / 1024).append(" MB (pss), ").append(x.getRss() / 1024).append(" MB (rss)")
                       .append(", status ").append(x.getStatus()).append(", importance ").append(x.getImportance()).append("\n");
                    if (r == ApplicationExitInfo.REASON_CRASH_NATIVE || r == ApplicationExitInfo.REASON_ANR) {
                        try (InputStream in = x.getTraceInputStream()) {
                            if (in != null) {
                                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                                String line; int n = 0;
                                while ((line = br.readLine()) != null && n < 40) { msg.append(line).append("\n"); n++; }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (newest > seen) diag().edit().putLong("seenExit", newest).apply();
                // One time: list every close Android has on record, whatever the reason, so we can see what's happening
                if (!diag().getBoolean("fullList1", false)) {
                    diag().edit().putBoolean("fullList1", true).apply();
                    msg.setLength(0);
                    msg.append("All recorded closes (newest first):\n");
                    if (list.isEmpty()) msg.append("(Android has none on record)\n");
                    for (ApplicationExitInfo x : list) {
                        msg.append(new SimpleDateFormat("MMM d h:mm:ss a", Locale.US).format(new Date(x.getTimestamp())))
                           .append("  ").append(reasonName(x.getReason()))
                           .append(x.getDescription() != null ? ": " + x.getDescription() : "")
                           .append(" [").append(x.getProcessName()).append(", ").append(x.getPss() / 1024).append(" MB]\n");
                    }
                }
            }
        } catch (Throwable ignored) {}
        String pending = diag().getString("pending", "");
        if (!pending.isEmpty()) { msg.append(pending); diag().edit().remove("pending").apply(); }
        if (msg.length() == 0) return;
        final String text = "Rolling Credits " + versionName() + " on Android " + Build.VERSION.RELEASE + "\n" + msg;
        getWindow().getDecorView().postDelayed(() -> showReport(text), 1500);
    }

    private void showReport(String text) {
        if (isFinishing() || isDestroyed()) return;
        try {
            new AlertDialog.Builder(this)
                .setTitle("Rolling Credits problem report")
                .setMessage(text.length() > 2500 ? text.substring(0, 2500) + "\u2026" : text)
                .setPositiveButton("Copy details", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("Rolling Credits crash", text));
                })
                .setNegativeButton("Close", null)
                .show();
        } catch (Throwable ignored) {}
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable e) { return ""; }
    }
}
