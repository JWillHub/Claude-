package io.github.jwillhub.rollingcredits;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.media.AudioManager;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
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
    private LinearLayout hud;        // small see-through pill: "+10s", or Volume/Brightness with a thin level bar
    private TextView hudLabel, hudPct;
    private FrameLayout hudBar;
    private View hudFill;
    private AudioManager audio;
    private int swipeMode = 0;       // 0 none, 1 volume, 2 brightness, -1 ignore (horizontal / edge)
    private float swipeStartLevel = 0f;

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

        // see-through pill with white text and a thin white level bar
        hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x59000000);
        pill.setCornerRadius(dp(22));
        pill.setStroke(dp(1), 0x33FFFFFF);
        hud.setBackground(pill);
        hudLabel = new TextView(this);
        hudLabel.setTextColor(Color.WHITE);
        hudLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hudLabel.setLetterSpacing(0.02f);
        hudBar = new FrameLayout(this);
        View track = new View(this);
        GradientDrawable trackBg = new GradientDrawable(); trackBg.setColor(0x40FFFFFF); trackBg.setCornerRadius(dp(2));
        track.setBackground(trackBg);
        hudFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable(); fillBg.setColor(Color.WHITE); fillBg.setCornerRadius(dp(2));
        hudFill.setBackground(fillBg);
        hudBar.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        hudBar.addView(hudFill, new FrameLayout.LayoutParams(0, dp(3)));
        hudPct = new TextView(this);
        hudPct.setTextColor(0xCCFFFFFF);
        hudPct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hudPct.setMinWidth(dp(34));
        hudPct.setGravity(Gravity.END);
        hud.addView(hudLabel);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(110), dp(3));
        barLp.leftMargin = dp(12); barLp.rightMargin = dp(10);
        hud.addView(hudBar, barLp);
        hud.addView(hudPct);
        hud.setAlpha(0f);
        hud.setVisibility(View.GONE);
        root.addView(hud, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setUpGestures();
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

    // ---- Touch gestures ----
    // Double-tap right/left: skip 10 s forward/back. Swipe up/down on the right: volume; on the left: brightness
    // (this screen only). Edges are left alone so Android's own back and notification swipes still work.
    private void setUpGestures() {
        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { swipeMode = 0; return true; }

            @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (view.isControllerFullyVisible()) view.hideController(); else view.showController();
                return true;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (exo == null) return true;
                boolean fwd = e.getX() > view.getWidth() / 2f;
                long dur = exo.getDuration(), to = exo.getCurrentPosition() + (fwd ? 10_000 : -10_000);
                if (dur != C.TIME_UNSET) to = Math.min(to, dur - 1000);
                exo.seekTo(Math.max(0, to));
                show(fwd ? "+10s  \u00bb" : "\u00ab  \u221210s", -1, fwd ? Gravity.CENTER_VERTICAL | Gravity.END : Gravity.CENTER_VERTICAL | Gravity.START);
                return true;
            }

            @Override public boolean onScroll(MotionEvent start, @NonNull MotionEvent now, float dx, float dy) {
                if (start == null) return false;
                float w = view.getWidth(), h = view.getHeight();
                if (swipeMode == 0) {
                    float mx = Math.abs(now.getX() - start.getX()), my = Math.abs(now.getY() - start.getY());
                    if (mx < dp(12) && my < dp(12)) return true;   // not moved enough to tell yet
                    boolean edge = start.getY() < h * 0.12f || start.getY() > h * 0.88f || start.getX() < w * 0.06f || start.getX() > w * 0.94f;
                    if (edge || my < mx * 1.5f) { swipeMode = -1; return false; }
                    swipeMode = start.getX() > w / 2f ? 1 : 2;
                    swipeStartLevel = swipeMode == 1 ? volumeLevel() : brightnessLevel();
                }
                if (swipeMode < 0) return false;
                float level = Math.max(0f, Math.min(1f, swipeStartLevel + (start.getY() - now.getY()) / (h * 0.7f)));
                if (swipeMode == 1) {
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0);
                    show("Volume", level, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                } else {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = Math.max(0.02f, level);
                    getWindow().setAttributes(lp);
                    show("Brightness", level, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                }
                return true;
            }
        });
        view.setOnTouchListener((v, e) -> {
            if (isPip()) return false;
            gd.onTouchEvent(e);
            return true;   // the player's own tap-to-show-controls is handled above
        });
    }

    private float volumeLevel() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max > 0 ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0.5f;
    }

    private float brightnessLevel() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b >= 0) return b;
        try { return android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f; }
        catch (Exception e) { return 0.5f; }
    }

    private final Runnable hideHud = () -> hud.animate().alpha(0f).setDuration(220).withEndAction(() -> hud.setVisibility(View.GONE)).start();

    // level < 0: just the text (skips); otherwise text + thin bar + percent
    private void show(String text, float level, int gravity) {
        hudLabel.setText(text);
        boolean bar = level >= 0;
        hudBar.setVisibility(bar ? View.VISIBLE : View.GONE);
        hudPct.setVisibility(bar ? View.VISIBLE : View.GONE);
        if (bar) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) hudFill.getLayoutParams();
            f.width = Math.round(dp(110) * level);
            hudFill.setLayoutParams(f);
            hudPct.setText(Math.round(level * 100) + "%");
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hud.getLayoutParams();
        lp.gravity = gravity;
        lp.topMargin = (gravity & Gravity.TOP) == Gravity.TOP ? dp(26) : 0;
        lp.leftMargin = lp.rightMargin = (gravity & Gravity.TOP) == Gravity.TOP ? 0 : dp(70);
        hud.setLayoutParams(lp);
        hud.removeCallbacks(hideHud);
        hud.animate().cancel();
        if (hud.getVisibility() != View.VISIBLE) { hud.setVisibility(View.VISIBLE); hud.setAlpha(0f); }
        hud.animate().alpha(1f).setDuration(120).start();
        hud.postDelayed(hideHud, 800);
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
