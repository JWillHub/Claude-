package io.github.jwillhub.lumen;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

// RCPlayer.play({ url, title, startMs, key, show, tmdbId, season, episode, queue, rdToken, autoplay }) opens the
// full-screen player. For episodes it can carry on through the following episodes itself (queue), skip intros and
// count down to the next episode. When it closes it answers with
// { positionMs, durationMs, ended, finished, lastKey, played:[{ key, positionMs, durationMs, finished }], error }.
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
        intent.putExtra("key", call.getString("key", ""));
        intent.putExtra("show", call.getString("show", ""));
        Integer tmdb = call.getInt("tmdbId", 0), s = call.getInt("season", 0), e = call.getInt("episode", 0);
        intent.putExtra("tmdbId", tmdb == null ? 0 : tmdb);
        intent.putExtra("season", s == null ? 0 : s);
        intent.putExtra("episode", e == null ? 0 : e);
        intent.putExtra("queue", call.getString("queue", "[]"));
        intent.putExtra("rdToken", call.getString("rdToken", ""));
        Boolean skip = call.getBoolean("autoSkip", false), mid = call.getBoolean("midnightDefault", false);
        intent.putExtra("autoSkip", skip != null && skip);
        intent.putExtra("midnightDefault", mid != null && mid);
        intent.putExtra("audioLang", call.getString("audioLang", ""));
        intent.putExtra("textLang", call.getString("textLang", ""));
        intent.putExtra("subSize", call.getString("subSize", "m"));
        intent.putExtra("subStyle", call.getString("subStyle", "shadow"));
        Boolean auto = call.getBoolean("autoplay", true);
        intent.putExtra("autoplay", auto == null || auto);
        startActivityForResult(call, intent, "playerClosed");
    }

    // The last spot the player wrote down (see PlayerActivity.saveSpot), for when a result never came back
    @PluginMethod
    public void saved(PluginCall call) {
        String j = getContext().getSharedPreferences("player", android.content.Context.MODE_PRIVATE).getString("spot", null);
        JSObject out = new JSObject();
        try { if (j != null) out = new JSObject(j); } catch (Exception ignored) {}
        call.resolve(out);
    }

    @PluginMethod
    public void clearSaved(PluginCall call) {
        getContext().getSharedPreferences("player", android.content.Context.MODE_PRIVATE).edit().remove("spot").apply();
        call.resolve();
    }

    @ActivityCallback
    private void playerClosed(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        JSObject out = new JSObject();
        out.put("positionMs", data != null ? data.getLongExtra("positionMs", 0L) : 0L);
        out.put("durationMs", data != null ? data.getLongExtra("durationMs", 0L) : 0L);
        out.put("ended", data != null && data.getBooleanExtra("ended", false));
        out.put("finished", data != null && data.getBooleanExtra("finished", false));
        String lastKey = data != null ? data.getStringExtra("lastKey") : null;
        if (lastKey != null) out.put("lastKey", lastKey);
        String played = data != null ? data.getStringExtra("played") : null;
        try { out.put("played", new JSArray(played != null ? played : "[]")); } catch (Exception ex) { out.put("played", new JSArray()); }
        String error = data != null ? data.getStringExtra("error") : null;
        if (error != null) out.put("error", error);
        call.resolve(out);
    }
}
