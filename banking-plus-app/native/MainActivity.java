package io.github.jwillhub.bankingplus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.BridgeActivity;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BridgeActivity {
    // "Save to" screen for backups and CSV exports. Registered here because Android only allows it before the screen starts.
    ActivityResultLauncher<Intent> saver;
    String pendingText = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        saver = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            boolean ok = false;
            String text = pendingText;
            pendingText = null;
            Uri uri = result.getData() != null ? result.getData().getData() : null;
            if (result.getResultCode() == RESULT_OK && uri != null && text != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) { os.write(text.getBytes(StandardCharsets.UTF_8)); ok = true; }
                } catch (Exception ignored) {}
            }
            tellPage("window.BPNativeSaved&&BPNativeSaved(" + ok + ")");
        });
        registerPlugin(BPNativePlugin.class);   // gives the page its BPNative helper (saving files, calendar, status bar)
        super.onCreate(savedInstanceState);

        // Android's back swipe: close the open pop-up first, then step back through the pages, then leave the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView web = getBridge() != null ? getBridge().getWebView() : null;
                if (web == null) { leave(); return; }
                web.evaluateJavascript("window.BPBack ? String(window.BPBack()) : 'false'", r -> {
                    if ("\"true\"".equals(r)) return;
                    if (web.canGoBack()) web.goBack(); else leave();
                });
            }
            private void leave() {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    void tellPage(String js) {
        runOnUiThread(() -> {
            try { getBridge().getWebView().evaluateJavascript(js, null); } catch (Exception ignored) {}
        });
    }
}
