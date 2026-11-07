package io.github.jwillhub.rollingcredits;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RCPlayerPlugin.class);   // the page opens the native player through this
        registerPlugin(RCUpdatePlugin.class);   // and installs app updates through this
        super.onCreate(savedInstanceState);
    }
}
