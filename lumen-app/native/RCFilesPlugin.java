package io.github.jwillhub.lumen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// RCFiles.save({ name, data, mime }) opens Android's "Save to" screen (Downloads, Drive, ...) and writes the file there
@CapacitorPlugin(name = "RCFiles")
public class RCFilesPlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String name = call.getString("name", "backup.json");
        String mime = call.getString("mime", "application/json");
        if (call.getString("data") == null) { call.reject("Nothing to save"); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(call, i, "saved");
    }

    @ActivityCallback
    private void saved(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject out = new JSObject();
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            out.put("saved", false);
            call.resolve(out);
            return;
        }
        Uri uri = data.getData();
        try (OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new Exception("could not open the file");
            os.write(call.getString("data", "").getBytes(StandardCharsets.UTF_8));
            out.put("saved", true);
            call.resolve(out);
        } catch (Exception e) {
            call.reject("Could not save: " + e.getMessage());
        }
    }
}
