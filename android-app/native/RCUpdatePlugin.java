package io.github.jwillhub.rollingcredits;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// RCUpdate.install({ url }) downloads the new APK into the app's cache and opens Android's installer.
// The first time, Android needs "Install unknown apps" allowed for this app: then it opens that setting instead.
@CapacitorPlugin(name = "RCUpdate")
public class RCUpdatePlugin extends Plugin {

    // Backup route: open the download link in the phone's browser
    @PluginMethod
    public void openInBrowser(PluginCall call) {
        String url = call.getString("url");
        if (url == null) { call.reject("No address"); return; }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void install(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) { call.reject("No download address"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            JSObject r = new JSObject();
            r.put("needsPermission", true);
            call.resolve(r);
            return;
        }
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                File dir = new File(getContext().getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("can't make the download folder");
                File apk = new File(dir, "rolling-credits.apk");
                URL u = new URL(url);
                for (int hop = 0; hop < 6; hop++) {   // GitHub redirects to its file host
                    c = (HttpURLConnection) u.openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "RollingCredits-Android");
                    c.setRequestProperty("Accept", "application/octet-stream");
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400) { u = new URL(u, c.getHeaderField("Location")); c.disconnect(); c = null; continue; }
                    if (code != 200) throw new IOException("HTTP " + code);
                    break;
                }
                if (c == null) throw new IOException("too many redirects");
                long total = c.getContentLengthLong(), got = 0;
                int lastPct = -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        int pct = total > 0 ? (int) (got * 100 / total) : -1;
                        if (pct != lastPct) {   // tell the page how far along the download is
                            lastPct = pct;
                            JSObject p = new JSObject();
                            p.put("pct", pct);
                            p.put("mb", Math.round(got / 104857.6) / 10.0);
                            notifyListeners("progress", p);
                        }
                    }
                }
                if (total > 0 && got < total) throw new IOException("download was cut off");
                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".updates", apk);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                JSObject r = new JSObject();
                r.put("started", true);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Download failed: " + e.getClass().getSimpleName() + (e.getMessage() != null ? " — " + e.getMessage() : ""));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }
}
