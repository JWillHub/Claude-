package io.github.jwillhub.bankingplus;

import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
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
    }
}
