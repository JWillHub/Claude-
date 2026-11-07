package io.github.jwillhub.rollingcredits;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

// Full-screen player. Uses the phone's own video decoders (4K HEVC included) and an FFmpeg
// decoder for audio the phone can't play (DTS, TrueHD), so MKV files play like they do in VLC.
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private boolean done = false;
    private String error = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        Intent in = getIntent();
        String url = in.getStringExtra("url");
        String title = in.getStringExtra("title");
        long startMs = in.getLongExtra("startMs", 0L);

        PlayerView view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);
        setContentView(view);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true);
        // Start after ~1 s of video (was 2.5 s); keep up to 3 minutes ready, capped at 300 MB of memory while playing
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
        player = new ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(sources)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build();
        view.setPlayer(player);

        MediaItem item = new MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
            .build();
        player.setMediaItem(item);
        if (startMs > 0) player.seekTo(startMs);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) close(true);
            }
            @Override
            public void onPlayerError(@NonNull PlaybackException e) {
                error = e.getErrorCodeName();
                close(false);
            }
        });
        player.prepare();
        player.setPlayWhenReady(true);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { close(false); }
        });
    }

    // Hand the position back to the page, then leave
    private void close(boolean ended) {
        if (done) return;
        done = true;
        Intent out = new Intent();
        if (player != null) {
            long pos = player.getCurrentPosition();
            long dur = player.getDuration();
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
        if (player != null && !isFinishing()) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
