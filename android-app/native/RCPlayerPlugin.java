package io.github.jwillhub.rollingcredits;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

// RCPlayer.play({ url, title, startMs }) opens the full-screen player and, when it closes,
// answers with { positionMs, durationMs, ended, error } so the page can save the resume point.
@CapacitorPlugin(name = "RCPlayer")
public class RCPlayerPlugin extends Plugin {

    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) { call.reject("No video address"); return; }
        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", call.getString("title", ""));
        Double start = call.getDouble("startMs", 0.0);
        intent.putExtra("startMs", start == null ? 0L : start.longValue());
        startActivityForResult(call, intent, "playerClosed");
    }

    @ActivityCallback
    private void playerClosed(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        JSObject out = new JSObject();
        out.put("positionMs", data != null ? data.getLongExtra("positionMs", 0L) : 0L);
        out.put("durationMs", data != null ? data.getLongExtra("durationMs", 0L) : 0L);
        out.put("ended", data != null && data.getBooleanExtra("ended", false));
        String error = data != null ? data.getStringExtra("error") : null;
        if (error != null) out.put("error", error);
        call.resolve(out);
    }
}
