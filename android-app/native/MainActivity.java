package io.github.jwillhub.rollingcredits;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RCPlayerPlugin.class);   // the page opens the native player through this
        registerPlugin(RCUpdatePlugin.class);   // and installs app updates through this
        super.onCreate(savedInstanceState);

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
