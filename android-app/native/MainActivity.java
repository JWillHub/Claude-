package io.github.jwillhub.rollingcredits;

import android.os.Bundle;
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
        super.onCreate(savedInstanceState);

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
}
