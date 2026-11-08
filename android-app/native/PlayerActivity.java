package io.github.jwillhub.rollingcredits;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.ui.PlayerView;

// Full-screen player. Uses the phone's own video decoders (4K HEVC included) and an FFmpeg
// decoder for audio the phone can't play (DTS, TrueHD), so MKV files play like they do in VLC.
// Also: remembers your audio/subtitle language and shrinks to picture-in-picture when you leave.
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer exo;
    private Player current;
    private PlayerView view;
    private LinearLayout bar;
    private MediaItem item;
    private boolean done = false, wasPip = false, userPickedTracks = false;
    private String error = null;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        prefs = getSharedPreferences("player", MODE_PRIVATE);

        Intent in = getIntent();
        String url = in.getStringExtra("url");
        String title = in.getStringExtra("title");
        long startMs = in.getLongExtra("startMs", 0L);

        view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);

        // Top bar over the video: back arrow and title. Shows and hides with the player's controls.
        FrameLayout root = new FrameLayout(this);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(28), dp(14), dp(28), dp(14));
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setPadding(dp(10), dp(2), dp(16), dp(6));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> close(false));
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setShadowLayer(6f, 0f, 1f, Color.BLACK);
        bar.addView(back);
        bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.setBackgroundColor(0x66000000);
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> bar.setVisibility(isPip() ? View.GONE : visibility));
        setContentView(root);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true);
        // Start after ~1 s of video; keep up to 3 minutes ready, capped at 300 MB of memory while playing
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 180_000, 1_000, 2_500)
            .setTargetBufferBytes(300 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build();
        // Follow Real-Debrid's redirects and allow slower servers a little longer before giving up
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000);
        // Files without a seek index (some AVI/TS) can still be skipped through quickly; MKV and MP4 use their own index
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http), extractors);
        exo = new ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(sources)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build();

        // Start with the audio and subtitle languages you picked last time
        String audio = prefs.getString("audioLang", null), text = prefs.getString("textLang", null);
        TrackSelectionParameters.Builder tp = exo.getTrackSelectionParameters().buildUpon();
        if (audio != null && !audio.isEmpty()) tp.setPreferredAudioLanguage(audio);
        if (text != null && !text.isEmpty()) tp.setPreferredTextLanguage(text);
        exo.setTrackSelectionParameters(tp.build());

        item = new MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeFor(url))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
            .build();
        exo.setMediaItem(item);
        if (startMs > 0) exo.seekTo(startMs);
        exo.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && current == exo) close(true);
            }
            @Override
            public void onPlayerError(@NonNull PlaybackException e) {
                error = e.getErrorCodeName();
                close(false);
            }
            @Override
            public void onTrackSelectionParametersChanged(@NonNull TrackSelectionParameters p) {
                userPickedTracks = true;   // you changed audio or subtitles in the player's menu
            }
            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                if (userPickedTracks) rememberTracks(tracks);
            }
            @Override
            public void onIsPlayingChanged(boolean playing) { updatePip(); }
        });
        current = exo;
        view.setPlayer(exo);
        exo.prepare();
        exo.setPlayWhenReady(true);

        updatePip();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { close(false); }
        });
    }

    // ---- Remember track choices ----
    private void rememberTracks(Tracks tracks) {
        String audio = null, text = "";
        for (Tracks.Group g : tracks.getGroups()) {
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSelected(i)) continue;
                Format f = g.getTrackFormat(i);
                if (g.getType() == C.TRACK_TYPE_AUDIO && f.language != null) audio = f.language;
                if (g.getType() == C.TRACK_TYPE_TEXT) text = f.language != null ? f.language : "und";
            }
        }
        SharedPreferences.Editor e = prefs.edit();
        if (audio != null) e.putString("audioLang", audio);
        e.putString("textLang", text);   // "" means subtitles off
        e.apply();
        userPickedTracks = false;
    }

    // ---- Picture-in-picture ----
    private boolean isPip() { return Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode(); }

    private void updatePip() {
        if (Build.VERSION.SDK_INT < 26) return;
        try { setPictureInPictureParams(pipParams()); } catch (Exception ignored) {}
    }

    private PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(current == exo && exo != null && exo.isPlaying());   // swipe home while playing: keep watching in a small window
            b.setSeamlessResizeEnabled(true);
        }
        return b.build();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= 26 && Build.VERSION.SDK_INT < 31 && current == exo && exo.isPlaying()) {
            try { enterPictureInPictureMode(pipParams()); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config);
        if (inPip) wasPip = true;
        view.setUseController(!inPip);
        bar.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }

    private static String mimeFor(String url) {
        String u = url == null ? "" : url.toLowerCase().split("\\?")[0];
        if (u.endsWith(".mkv")) return MimeTypes.VIDEO_MATROSKA;
        if (u.endsWith(".webm")) return MimeTypes.VIDEO_WEBM;
        if (u.endsWith(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (u.endsWith(".avi")) return MimeTypes.VIDEO_AVI;
        if (u.endsWith(".ts")) return MimeTypes.VIDEO_MP2T;
        return MimeTypes.VIDEO_MP4;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // Hand the position back to the page, then leave
    private void close(boolean ended) {
        if (done) return;
        done = true;
        Intent out = new Intent();
        if (current != null) {
            long pos = current.getCurrentPosition();
            long dur = current.getDuration();
            out.putExtra("positionMs", pos);
            out.putExtra("durationMs", dur == C.TIME_UNSET ? 0L : dur);
        }
        out.putExtra("ended", ended);
        if (error != null) out.putExtra("error", error);
        setResult(RESULT_OK, out);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // keep playing in picture-in-picture or on the TV; otherwise pause
        if (exo != null && current == exo && !isFinishing() && !isPip()) exo.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // the floating window was closed: save your place and return
        if (wasPip && !isFinishing() && current == exo) close(false);
    }

    @Override
    protected void onDestroy() {
        if (exo != null) { exo.release(); exo = null; }
        super.onDestroy();
    }
}
