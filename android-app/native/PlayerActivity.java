package io.github.jwillhub.rollingcredits;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.audiofx.DynamicsProcessing;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageButton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.media.AudioManager;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
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
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

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
    private TextView nameView;

    // ---- episodes in a row, skip intro, midnight mode ----
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private String rdToken = "", show = "", curKey = "";
    private int tmdbId = 0, curS = 0, curE = 0, qIndex = -1;
    private boolean autoplay = true, curFinished = false, nextShown = false, nextCancelled = false, loadingNext = false;
    private JSONArray queue = new JSONArray();
    private final JSONArray played = new JSONArray();
    private String preUrl = null; private int preFor = -2; private boolean prefetching = false;
    private long creditsMs = -1, skipTarget = 0;
    private final List<long[]> segs = new ArrayList<>();      // {start, end, kind 0 intro / 1 recap}
    private TextView skipBtn, nextBtn;
    private ClipDrawable nextFill;
    private int countdown = 0;
    private boolean counting = false;
    private long lastSkipAt = 0; private int runDir = 0, runTotal = 0;
    private float downX, downY; private boolean runTap = false;
    private DynamicsProcessing dyn;
    private boolean midnight = false;
    private boolean autoSkip = false;
    private final java.util.Set<Long> autoSkipped = new java.util.HashSet<>();
    private long undoTo = 0, undoUntil = 0;
    private ImageButton moonBtn; private MoonDrawable moon;

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
        curKey = str(in.getStringExtra("key"));
        show = str(in.getStringExtra("show"));
        rdToken = str(in.getStringExtra("rdToken"));
        tmdbId = in.getIntExtra("tmdbId", 0);
        curS = in.getIntExtra("season", 0);
        curE = in.getIntExtra("episode", 0);
        autoplay = in.getBooleanExtra("autoplay", true);
        try { queue = new JSONArray(str(in.getStringExtra("queue")).isEmpty() ? "[]" : in.getStringExtra("queue")); } catch (Exception e) { queue = new JSONArray(); }
        midnight = in.getBooleanExtra("midnightDefault", false);   // Settings → Player → Midnight mode by default
        autoSkip = in.getBooleanExtra("autoSkip", false);

        view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);
        styleSubtitles(str(in.getStringExtra("subSize")), str(in.getStringExtra("subStyle")));

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
        nameView = name;
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

        // bottom-right: "Skip intro" and "Next episode" (see-through pills with a white outline)
        skipBtn = pillButton();
        skipBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (exo == null) return;
            if (SystemClock.uptimeMillis() < undoUntil) { exo.seekTo(undoTo); undoUntil = 0; }   // undo an automatic skip
            else exo.seekTo(skipTarget);
            fade(skipBtn, false);
        });
        nextBtn = pillButton();
        GradientDrawable fillShape = new GradientDrawable(); fillShape.setColor(0x40FFFFFF); fillShape.setCornerRadius(dp(22));
        nextFill = new ClipDrawable(fillShape, Gravity.START, ClipDrawable.HORIZONTAL);
        nextBtn.setBackground(new LayerDrawable(new Drawable[]{ nextBtn.getBackground(), nextFill }));
        nextBtn.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); goNext(); });
        for (TextView b : new TextView[]{ skipBtn, nextBtn }) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            lp.rightMargin = dp(36); lp.bottomMargin = dp(96);
            root.addView(b, lp);
        }
        setUpGestures();
        setContentView(root);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true);
        // Start after ~1 s of video; keep up to 3 minutes ready, capped at 160 MB of memory while playing.
        // (The buffer lives in the app's own memory; 300 MB went past what Android allows and crashed on big 4K files.)
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 180_000, 1_000, 2_500)
            .setTargetBufferBytes(160 * 1024 * 1024)
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
        // Settings → Player languages; "Last used" falls back to what you picked in the player before
        String setA = str(in.getStringExtra("audioLang")), setT = str(in.getStringExtra("textLang"));
        String audio = !setA.isEmpty() ? setA : prefs.getString("audioLang", null);
        String text = !setT.isEmpty() ? setT : prefs.getString("textLang", null);
        TrackSelectionParameters.Builder tp = exo.getTrackSelectionParameters().buildUpon();
        if (audio != null && !audio.isEmpty()) tp.setPreferredAudioLanguage(audio);
        if ("off".equals(text)) { tp.setPreferredTextLanguage(null); tp.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT); }
        else if (text != null && !text.isEmpty()) tp.setPreferredTextLanguage(text);
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
                if (state != Player.STATE_ENDED || current != exo) return;
                curFinished = true;
                if (hasNext() && autoplay && !nextCancelled) goNext(); else close(true);
            }
            @Override
            public void onPlayerError(@NonNull PlaybackException e) { handleError(e); }
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
            @Override
            public void onAudioSessionIdChanged(int id) { applyMidnight(); }
        });
        current = exo;
        view.setPlayer(exo);
        exo.prepare();
        exo.setPlayWhenReady(true);

        updatePip();
        applyMidnight();
        addMoonButton();
        fetchSegments();
        ui.postDelayed(tick, 500);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { if (counting) cancelNext(); else close(false); }
        });
    }

    // ---- Touch gestures ----
    // Double-tap right/left: skip 10 s forward/back. Swipe up/down on the right: volume; on the left: brightness
    // (this screen only). Edges are left alone so Android's own back and notification swipes still work.
    private void setUpGestures() {
        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { swipeMode = 0; return true; }

            @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (counting) { cancelNext(); return true; }
                if (view.isControllerFullyVisible()) view.hideController(); else view.showController();
                return true;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (exo == null) return true;
                runDir = 0;   // a fresh double-tap always starts a new run
                skipTap(e.getX() > view.getWidth() / 2f);
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
            int a = e.getActionMasked();
            // right after a double-tap skip, each quick extra tap adds another 10 s
            if (a == MotionEvent.ACTION_DOWN) { downX = e.getX(); downY = e.getY(); runTap = runDir != 0 && SystemClock.uptimeMillis() - lastSkipAt < 700; }
            if (runTap) {
                if (a == MotionEvent.ACTION_UP && Math.abs(e.getX() - downX) < dp(24) && Math.abs(e.getY() - downY) < dp(24)) skipTap(e.getX() > view.getWidth() / 2f);
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) runTap = false;
                return true;
            }
            gd.onTouchEvent(e);
            return true;   // the player's own tap-to-show-controls is handled above
        });
    }

    private void skipTap(boolean fwd) {
        if (exo == null) return;
        int dir = fwd ? 1 : -1;
        long now = SystemClock.uptimeMillis();
        if (dir != runDir || now - lastSkipAt > 700) { runDir = dir; runTotal = 0; }
        runTotal += 10; lastSkipAt = now;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        long dur = exo.getDuration(), to = exo.getCurrentPosition() + dir * 10_000L;
        if (dur != C.TIME_UNSET) to = Math.min(to, dur - 1000);
        exo.seekTo(Math.max(0, to));
        show(fwd ? "+" + runTotal + "s  \u00bb" : "\u00ab  \u2212" + runTotal + "s", -1, fwd ? Gravity.CENTER_VERTICAL | Gravity.END : Gravity.CENTER_VERTICAL | Gravity.START);
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

    private static String str(String s) { return s == null ? "" : s; }

    // Settings → Player → subtitle size and style
    private void styleSubtitles(String size, String style) {
        SubtitleView sv = view.getSubtitleView();
        if (sv == null) return;
        float f = "s".equals(size) ? 0.85f : "l".equals(size) ? 1.3f : "xl".equals(size) ? 1.6f : 1.05f;
        sv.setApplyEmbeddedStyles(false);
        sv.setApplyEmbeddedFontSizes(false);
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * f);
        int fg = "yellow".equals(style) ? 0xFFFFE14D : Color.WHITE;
        int bg = "box".equals(style) ? 0xB3000000 : Color.TRANSPARENT;
        int edge = "outline".equals(style) ? CaptionStyleCompat.EDGE_TYPE_OUTLINE : "box".equals(style) ? CaptionStyleCompat.EDGE_TYPE_NONE : CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW;
        sv.setStyle(new CaptionStyleCompat(fg, bg, Color.TRANSPARENT, edge, Color.BLACK, null));
    }

    // Write where you are to the phone every few seconds, so the app still gets your resume point
    // if the player never closes normally (app swiped away, phone restarts, Android closes it in the background).
    private long lastSaveAt = 0;
    private void saveSpot() {
        lastSaveAt = SystemClock.uptimeMillis();
        if (exo == null || curKey.isEmpty() || loadingNext) return;
        try {
            long pos = exo.getCurrentPosition(), dur = exo.getDuration();
            if (dur == C.TIME_UNSET || dur <= 0) return;
            JSONObject o = new JSONObject();
            o.put("lastKey", curKey); o.put("positionMs", pos); o.put("durationMs", dur);
            o.put("finished", curFinished); o.put("played", played.toString()); o.put("at", System.currentTimeMillis());
            prefs.edit().putString("spot", o.toString()).commit();
        } catch (Exception ignored) {}
    }

    private TextView pillButton() {
        TextView b = new TextView(this);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setPadding(dp(20), dp(11), dp(20), dp(11));
        GradientDrawable bg0 = new GradientDrawable();
        bg0.setColor(0x59000000); bg0.setCornerRadius(dp(22)); bg0.setStroke(dp(1), 0xCCFFFFFF);
        b.setBackground(bg0);
        b.setVisibility(View.GONE); b.setAlpha(0f);
        return b;
    }

    private void fade(View v, boolean in) {
        if (in == (v.getVisibility() == View.VISIBLE)) return;
        v.animate().cancel();
        if (in) { v.setVisibility(View.VISIBLE); v.animate().alpha(1f).setDuration(180).start(); }
        else v.animate().alpha(0f).setDuration(180).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    // ---- Every half second: show "Skip intro" during an intro, start the next-episode countdown near the end ----
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (exo == null) return;
            try { check(); } catch (Exception ignored) {}
            if (SystemClock.uptimeMillis() - lastSaveAt > 8000) saveSpot();
            if (retries > 0 && exo.isPlaying() && SystemClock.uptimeMillis() - lastErrAt > 60_000) retries = 0;
            ui.postDelayed(this, 500);
        }
    };

    private void check() {
        if (loadingNext) return;
        long pos = exo.getCurrentPosition(), dur = exo.getDuration();
        String label = null; long segStart = -1;
        for (long[] sg : segs) {
            if (pos >= sg[0] && pos < sg[1] - 1500) { label = sg[2] == 1 ? "Skip recap" : "Skip intro"; skipTarget = sg[1]; segStart = sg[0]; break; }
        }
        // Settings → Player → Skip intros automatically: jump once per intro, then offer "Undo skip" for 5 seconds
        if (label != null && autoSkip && !isPip() && !autoSkipped.contains(segStart)) {
            autoSkipped.add(segStart);
            undoTo = pos; undoUntil = SystemClock.uptimeMillis() + 5000;
            exo.seekTo(skipTarget);
            show(label.equals("Skip recap") ? "Skipped recap" : "Skipped intro", -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            label = null;
        }
        if (SystemClock.uptimeMillis() < undoUntil && !isPip()) { skipBtn.setText("Undo skip"); fade(skipBtn, true); }
        else if (label != null && !isPip()) { skipBtn.setText(label); fade(skipBtn, true); } else fade(skipBtn, false);
        if (dur == C.TIME_UNSET || dur < 120_000) return;
        long trigger = creditsMs > dur / 2 && creditsMs < dur - 5000 ? creditsMs : (long) (dur * 0.95);
        if (hasNext() && pos > dur * 0.7) prefetchNext();
        if (!nextShown && pos >= trigger) {
            curFinished = true;   // counts as watched once the credits start
            if (hasNext()) showNext();
        }
        if (nextShown) nextBtn.setVisibility(isPip() ? View.GONE : View.VISIBLE);
    }

    private boolean hasNext() { return queue != null && qIndex + 1 < queue.length() && !rdToken.isEmpty(); }

    private void showNext() {
        nextShown = true;
        nextFill.setLevel(0);
        if (autoplay && !nextCancelled) { counting = true; countdown = 10; nextBtn.setText("Next episode  " + countdown); ui.postDelayed(countTick, 1000); }
        else nextBtn.setText("Next episode");
        fade(nextBtn, true);
    }

    private final Runnable countTick = new Runnable() {
        @Override public void run() {
            if (!counting) return;
            countdown--;
            nextFill.setLevel((10 - countdown) * 1000);
            if (countdown <= 0) { goNext(); return; }
            nextBtn.setText("Next episode  " + countdown);
            ui.postDelayed(this, 1000);
        }
    };

    private void cancelNext() {
        counting = false; nextCancelled = true;
        ui.removeCallbacks(countTick);
        nextFill.setLevel(0);
        nextBtn.setText("Next episode");
    }

    private void prefetchNext() {
        if (prefetching || preFor == qIndex + 1) return;
        JSONObject n = queue.optJSONObject(qIndex + 1); if (n == null) return;
        if (!n.optString("url", "").isEmpty()) { preFor = qIndex + 1; preUrl = n.optString("url"); return; }
        prefetching = true;
        final int want = qIndex + 1; final String link = n.optString("link", "");
        bg.execute(() -> {
            String u = null;
            try { u = unrestrict(link); } catch (Exception ignored) {}
            final String got = u;
            ui.post(() -> { prefetching = false; if (got != null) { preFor = want; preUrl = got; } });
        });
    }

    private void goNext() {
        if (loadingNext || !hasNext() || exo == null) return;
        counting = false; ui.removeCallbacks(countTick);
        try {
            JSONObject p = new JSONObject();
            p.put("key", curKey); p.put("positionMs", exo.getCurrentPosition());
            long d = exo.getDuration(); p.put("durationMs", d == C.TIME_UNSET ? 0L : d);
            p.put("finished", true);
            played.put(p);
        } catch (Exception ignored) {}
        final int want = qIndex + 1;
        final JSONObject n = queue.optJSONObject(want);
        if (n == null) { close(true); return; }
        loadingNext = true;
        fade(nextBtn, false); fade(skipBtn, false);
        exo.pause();
        show("Loading next episode\u2026", -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        String known = !n.optString("url", "").isEmpty() ? n.optString("url") : (preFor == want ? preUrl : null);
        if (known != null) { startEpisode(want, n, known); return; }
        bg.execute(() -> {
            String u = null;
            try { u = unrestrict(n.optString("link", "")); } catch (Exception ignored) {}
            final String got = u;
            ui.post(() -> {
                if (got == null) { loadingNext = false; error = "NEXT_EPISODE_UNAVAILABLE"; close(false); return; }
                startEpisode(want, n, got);
            });
        });
    }

    private void startEpisode(int index, JSONObject n, String url) {
        qIndex = index;
        curKey = n.optString("key", "");
        curS = n.optInt("s", 0); curE = n.optInt("e", 0);
        curFinished = false; nextShown = false; nextCancelled = false; counting = false;
        segs.clear(); creditsMs = -1; preUrl = null; preFor = -2; autoSkipped.clear(); undoUntil = 0;
        String title = show + (n.optString("label", "").isEmpty() ? "" : " \u00b7 " + n.optString("label"));
        nameView.setText(title);
        item = new MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeFor(url))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
            .build();
        exo.setMediaItem(item);
        long st = n.optLong("startMs", 0L);
        if (st > 0) exo.seekTo(st);
        exo.prepare();
        exo.setPlayWhenReady(true);
        loadingNext = false;
        fetchSegments();
    }

    // Real-Debrid: turn a library link into a playable address (same call the app makes)
    private String unrestrict(String link) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.real-debrid.com/rest/1.0/unrestrict/link").openConnection();
        c.setConnectTimeout(15_000); c.setReadTimeout(20_000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + rdToken);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream o = c.getOutputStream()) { o.write(("link=" + URLEncoder.encode(link, "UTF-8")).getBytes("UTF-8")); }
        if (c.getResponseCode() != 200) throw new Exception("rd " + c.getResponseCode());
        String u = new JSONObject(read(c)).optString("download", "");
        return u.isEmpty() ? null : u;
    }

    private static String read(HttpURLConnection c) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line; while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // TheIntroDB: community timestamps for intros, recaps and credits (episodes only)
    private void fetchSegments() {
        if (tmdbId <= 0 || curS <= 0 || curE <= 0) return;
        final String key = curKey; final int t = tmdbId, s0 = curS, e0 = curE;
        bg.execute(() -> {
            JSONObject j = null;
            for (String v : new String[]{ "v4", "v3" }) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("https://api.theintrodb.org/" + v + "/media?tmdb_id=" + t + "&season=" + s0 + "&episode=" + e0).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    if (code == 404) break;
                    if (code == 200) { j = new JSONObject(read(c)); break; }
                } catch (Exception ignored) {}
            }
            final JSONObject got = j;
            if (got == null) return;
            final List<long[]> found = new ArrayList<>();
            long credits = -1;
            String[] kinds = { "intro", "recap" };
            for (int k = 0; k < kinds.length; k++) {
                JSONArray arr = got.optJSONArray(kinds[k]); if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i); if (o == null || o.isNull("end_ms")) continue;
                    long st = o.isNull("start_ms") ? 0 : o.optLong("start_ms", 0), en = o.optLong("end_ms", 0);
                    if (en > st + 3000) found.add(new long[]{ st, en, k });
                }
            }
            JSONArray cr = got.optJSONArray("credits");
            if (cr != null && cr.length() > 0) { JSONObject o = cr.optJSONObject(0); if (o != null && !o.isNull("start_ms")) credits = o.optLong("start_ms", -1); }
            final long cf = credits;
            ui.post(() -> { if (!key.equals(curKey)) return; segs.clear(); segs.addAll(found); creditsMs = cf; });
        });
    }

    // ---- Midnight mode: evens out loud and quiet parts (compressor + a ceiling on sudden peaks) ----
    private void applyMidnight() {
        if (Build.VERSION.SDK_INT < 28 || exo == null) return;
        try {
            if (dyn != null) { dyn.release(); dyn = null; }
            if (!midnight) return;
            int sid = exo.getAudioSessionId();
            if (sid == C.AUDIO_SESSION_ID_UNSET || sid == 0) return;
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, true, 1, false, 0, true).build();
            dyn = new DynamicsProcessing(0, sid, cfg);
            // above -32 dB, every 3.5 dB louder only comes out 1 dB louder; then quiet parts are lifted 8 dB
            dyn.setMbcBandAllChannelsTo(0, new DynamicsProcessing.MbcBand(true, 20000f, 5f, 250f, 3.5f, -32f, 6f, -90f, 1f, 0f, 8f));
            // hard ceiling: nothing goes above -9 dB
            dyn.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 80f, 12f, -9f, 0f));
            dyn.setEnabled(true);
        } catch (Throwable t) { dyn = null; }
    }

    private void toggleMidnight() {
        midnight = !midnight;
        applyMidnight();
        if (moon != null) { moon.on = midnight; moon.invalidateSelf(); }
        show(midnight ? "Midnight mode on" : "Midnight mode off", -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    }

    // a moon button next to the player's CC button (or in the top bar if the player's layout changes)
    private void addMoonButton() {
        moon = new MoonDrawable(); moon.on = midnight;
        moonBtn = new ImageButton(this);
        moonBtn.setImageDrawable(moon);
        moonBtn.setContentDescription("Midnight mode");
        moonBtn.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); toggleMidnight(); });
        try {
            View cc = view.findViewById(androidx.media3.ui.R.id.exo_subtitle);
            ViewGroup parent = (ViewGroup) cc.getParent();
            moonBtn.setBackground(cc.getBackground() != null && cc.getBackground().getConstantState() != null ? cc.getBackground().getConstantState().newDrawable().mutate() : null);
            moonBtn.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            moonBtn.setPadding(cc.getPaddingLeft(), cc.getPaddingTop(), cc.getPaddingRight(), cc.getPaddingBottom());
            parent.addView(moonBtn, parent.indexOfChild(cc), new ViewGroup.LayoutParams(cc.getLayoutParams()));
        } catch (Exception e) {
            moonBtn.setBackground(null);
            moonBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
            bar.addView(moonBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
    }

    private static class MoonDrawable extends Drawable {
        boolean on = false;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY(), r = size * 0.3f;
            Path p = new Path(); p.addCircle(cx, cy, r, Path.Direction.CW);
            Path cut = new Path(); cut.addCircle(cx + r * 0.55f, cy - r * 0.42f, r * 0.82f, Path.Direction.CW);
            p.op(cut, Path.Op.DIFFERENCE);
            paint.setColor(Color.WHITE);
            paint.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.06f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(p, paint);
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 96; }
        @Override public int getIntrinsicHeight() { return 96; }
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
        if (inPip) { skipBtn.setVisibility(View.GONE); nextBtn.setVisibility(View.GONE); }
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

    // ---- Playback errors: reconnect on its own, and if that doesn't work, say what went wrong instead of closing ----
    private int retries = 0;
    private long lastErrAt = 0, resumeAt = 0;
    private LinearLayout errPanel;
    private TextView errText;

    private void handleError(PlaybackException e) {
        if (exo == null || done) return;
        lastErrAt = SystemClock.uptimeMillis();
        long pos = exo.getCurrentPosition();
        if (pos > 0) resumeAt = pos;
        int code = e.errorCode;
        String detail = e.getErrorCodeName();
        Throwable c = e.getCause();
        if (c instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
            detail += " (HTTP " + ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) c).responseCode + ")";
        else if (c != null && c.getMessage() != null) detail += " (" + c.getClass().getSimpleName() + ": " + (c.getMessage().length() > 120 ? c.getMessage().substring(0, 120) : c.getMessage()) + ")";
        boolean network = code >= 2000 && code < 3000;
        int maxTries = network ? 6 : 1;   // connection trouble: keep trying; decoder/file trouble: one more go
        logError(detail + " at " + fmt(resumeAt) + (retries < maxTries ? ", retrying" : ", gave up"));
        if (retries < maxTries) {
            retries++;
            show(network ? "Connection lost \u00b7 reconnecting\u2026" : "Playback hiccup \u00b7 retrying\u2026", -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            final long at = resumeAt;
            ui.postDelayed(() -> {
                if (exo == null || done) return;
                exo.seekTo(at); exo.prepare(); exo.setPlayWhenReady(true);
            }, Math.min(1000L * retries * retries, 15_000L));
            return;
        }
        error = e.getErrorCodeName();
        showErrorPanel(friendly(code) + "\n\n" + detail);
    }

    private static String friendly(int code) {
        if (code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return "Real-Debrid refused the file. The link may have expired. Close and press Play again.";
        if (code >= 2000 && code < 3000) return "Lost the connection to Real-Debrid and couldn't get it back.";
        if (code >= 3000 && code < 4000) return "This file looks damaged or uses a format the player can't read. Try another version, or VLC.";
        if (code >= 4000 && code < 5000) return "The phone couldn't decode this video or audio. Try another version, or VLC.";
        return "Playback stopped.";
    }

    private String fmt(long ms) { long t = ms / 1000; return String.format(java.util.Locale.US, "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60); }

    // also goes into the "closed unexpectedly" report the app shows on its next start
    private void logError(String text) {
        try {
            SharedPreferences d = getSharedPreferences("diag", MODE_PRIVATE);
            String when = new java.text.SimpleDateFormat("MMM d h:mm:ss a", java.util.Locale.US).format(new java.util.Date());
            String line = when + "  Player: " + text + (nameView != null ? " [" + nameView.getText() + "]" : "") + "\n";
            String prev = d.getString("pending", "");
            if (prev.length() > 4000) prev = prev.substring(prev.length() - 4000);
            d.edit().putString("pending", prev + line).apply();
        } catch (Throwable ignored) {}
    }

    private void showErrorPanel(String text) {
        if (errPanel == null) {
            errPanel = new LinearLayout(this);
            errPanel.setOrientation(LinearLayout.VERTICAL);
            errPanel.setGravity(Gravity.CENTER_HORIZONTAL);
            errPanel.setPadding(dp(24), dp(20), dp(24), dp(20));
            GradientDrawable bgp = new GradientDrawable(); bgp.setColor(0xD9000000); bgp.setCornerRadius(dp(18)); bgp.setStroke(dp(1), 0x33FFFFFF);
            errPanel.setBackground(bgp);
            errText = new TextView(this);
            errText.setTextColor(Color.WHITE);
            errText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            errText.setGravity(Gravity.CENTER);
            errText.setTextIsSelectable(true);
            errPanel.addView(errText, new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            TextView again = pillButton(); again.setText("Try again"); again.setVisibility(View.VISIBLE); again.setAlpha(1f);
            TextView quit = pillButton(); quit.setText("Close"); quit.setVisibility(View.VISIBLE); quit.setAlpha(1f);
            again.setOnClickListener(v -> {
                errPanel.setVisibility(View.GONE); error = null; retries = 0;
                if (exo != null) { exo.seekTo(resumeAt); exo.prepare(); exo.setPlayWhenReady(true); }
            });
            quit.setOnClickListener(v -> close(false));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMargins(dp(8), dp(18), dp(8), 0);
            row.addView(again, bl); row.addView(quit, new LinearLayout.LayoutParams(bl));
            errPanel.addView(row);
            ((FrameLayout) view.getParent()).addView(errPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        errText.setText(text);
        errPanel.setVisibility(View.VISIBLE);
        errPanel.bringToFront();
        view.hideController();
    }

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
        out.putExtra("finished", curFinished || ended);
        out.putExtra("lastKey", curKey);
        out.putExtra("played", played.toString());
        saveSpot();
        if (error != null) out.putExtra("error", error);
        setResult(RESULT_OK, out);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSpot();
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
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        if (dyn != null) { try { dyn.release(); } catch (Exception ignored) {} dyn = null; }
        if (exo != null) { exo.release(); exo = null; }
        super.onDestroy();
    }
}
