package io.github.jwillhub.bankingplus;

import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;
import android.os.Build;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Adds window.BPNative to the page. It is a plain Android bridge object rather than a Capacitor plugin call,
// so the page keeps its strict security policy (no extra scripts are loaded into it).
@CapacitorPlugin(name = "BPNative")
public class BPNativePlugin extends Plugin {

    @Override
    public void load() {
        // runs before the page is loaded, so BPNative is there from the first line of the page
        getBridge().getWebView().addJavascriptInterface(new Api(), "BPNative");
    }

    class Api {
        // Backup / CSV: opens Android's "Save to" screen (Downloads, Drive, ...). The page hears back through BPNativeSaved(ok).
        @JavascriptInterface
        public void saveFile(String name, String mime, String text) {
            MainActivity a = (MainActivity) getActivity();
            a.runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                a.pendingText = text;
                try { a.saver.launch(i); }
                catch (Exception e) { a.pendingText = null; a.tellPage("window.BPNativeSaved&&BPNativeSaved(false)"); }
            });
        }

        // Calendar reminders: hands the .ics file to the calendar app
        @JavascriptInterface
        public boolean openFile(String name, String mime, String text) {
            try {
                File dir = new File(getContext().getCacheDir(), "shared");
                dir.mkdirs();
                File f = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(text.getBytes(StandardCharsets.UTF_8)); }
                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".files", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getActivity().runOnUiThread(() -> {
                    try { getActivity().startActivity(Intent.createChooser(i, "Add to calendar")); } catch (Exception ignored) {}
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // Status bar and navigation bar icons: dark on the light look, light on the dark look
        @JavascriptInterface
        public void setBars(boolean light) {
            getActivity().runOnUiThread(() -> {
                try {
                    android.view.Window w = getActivity().getWindow();
                    WindowInsetsControllerCompat c = new WindowInsetsControllerCompat(w, w.getDecorView());
                    c.setAppearanceLightStatusBars(light);
                    c.setAppearanceLightNavigationBars(light);
                    w.getDecorView().setBackgroundColor(light ? 0xFFDCEBFA : 0xFF000000);
                } catch (Exception ignored) {}
            });
        }
            // ---- app updates (like the other apps): only the version number and the new app file come from GitHub; nothing is sent ----
        final String REL = "https://github.com/JWillHub/Claude-/releases/download/banking-plus-latest/";

        // this copy's build number
        @JavascriptInterface
        public int build() {
            try {
                android.content.pm.PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
                return (int) androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi);
            } catch (Exception e) { return 0; }
        }

        // asks GitHub for the newest build number; answers through BPUpdate.checked(n), or -1 when it couldn't check
        @JavascriptInterface
        public void checkUpdate() {
            new Thread(() -> {
                int n = -1;
                HttpURLConnection c = null;
                try {
                    c = open(REL + "version.json?t=" + System.currentTimeMillis());
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    try (InputStream in = c.getInputStream()) { byte[] buf = new byte[4096]; int k; while ((k = in.read(buf)) > 0) b.write(buf, 0, k); }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"build\"\\s*:\\s*(\\d+)").matcher(b.toString("UTF-8"));
                    if (m.find()) n = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {
                } finally { if (c != null) c.disconnect(); }
                ((MainActivity) getActivity()).tellPage("window.BPUpdate&&BPUpdate.checked(" + n + ")");
            }).start();
        }

        // downloads the newest app and opens Android's installer. The first time, Android asks to allow installs from Banking+.
        @JavascriptInterface
        public void installUpdate() {
            MainActivity a = (MainActivity) getActivity();
            if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
                a.runOnUiThread(() -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
                        a.startActivity(i);
                    } catch (Exception ignored) {}
                });
                a.tellPage("window.BPUpdate&&BPUpdate.needsPermission()");
                return;
            }
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    File dir = new File(getContext().getCacheDir(), "updates");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("can't make the download folder");
                    File apk = new File(dir, "banking-plus.apk");
                    c = open(REL + "banking-plus.apk?t=" + System.currentTimeMillis());
                    long total = c.getContentLengthLong(), got = 0;
                    int last = -2, lastMb = -1;
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                        byte[] buf = new byte[65536]; int k;
                        while ((k = in.read(buf)) > 0) {
                            out.write(buf, 0, k); got += k;
                            int pct = total > 0 ? (int) (got * 100 / total) : -1;
                            int mbx = (int) (got / 262144); if (pct != last || (pct < 0 && mbx != lastMb)) { last = pct; lastMb = mbx; a.tellPage("window.BPUpdate&&BPUpdate.progress(" + pct + "," + (got / 1048576.0) + ")"); }
                        }
                    }
                    if (total > 0 && got < total) throw new IOException("the download was cut off");
                    Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".files", apk);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                    a.tellPage("window.BPUpdate&&BPUpdate.started()");
                } catch (Exception e) {
                    String msg = (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()).replaceAll("[^A-Za-z0-9 .,:-]", " ");
                    a.tellPage("window.BPUpdate&&BPUpdate.failed('" + msg + "')");
                } finally { if (c != null) c.disconnect(); }
            }).start();
        }

        // backup route for updates: open the download in the phone's browser
        @JavascriptInterface
        public void openUrl(String url) {
            if (url == null || !url.startsWith("https://")) return;
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (Exception ignored) {}
        }

        // GitHub sends release files from another address, so follow its redirects
        private HttpURLConnection open(String address) throws IOException {
            URL u = new URL(address);
            for (int hop = 0; hop < 6; hop++) {
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "BankingPlus-Android");
                c.setRequestProperty("Accept", "application/octet-stream");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) { u = new URL(u, c.getHeaderField("Location")); c.disconnect(); continue; }
                if (code != 200) { c.disconnect(); throw new IOException("GitHub answered " + code); }
                return c;
            }
            throw new IOException("too many redirects");
        }
    }
}
