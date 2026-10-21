package com.orbit.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int PICK_MEDIA = 61, PICK_SUBTITLE = 62, PERMISSION = 63;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Entry> tracks = new ArrayList<>();
    private final ArrayList<Entry> displayed = new ArrayList<>();
    private MediaController player;
    private ListenableFuture<MediaController> future;
    private LinearLayout root, listing, actions;
    private PlayerView playerView;
    private EditText search, stream;
    private TextView now, count;
    private Button playButton, repeatButton, shuffleButton;
    private Uri lastUri;
    private boolean video = true;
    private String filter = "All";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && now != null) {
                long dur = player.getDuration(), pos = player.getCurrentPosition();
                String label = player.getCurrentMediaItem() == null ? "No media playing" :
                        String.valueOf(player.getCurrentMediaItem().mediaMetadata.title);
                now.setText(label + (dur > 0 && dur != C.TIME_UNSET ?
                        "  •  " + clock(pos) + " / " + clock(dur) : ""));
                if (playButton != null) playButton.setText(player.isPlaying() ? "Pause" : "Play");
            }
            handler.postDelayed(this, 700);
        }
    };
    private static final class Entry {
        final Uri uri;
        final String title;
        final boolean video;
        Entry(Uri uri, String title, boolean video) {
            this.uri = uri; this.title = title; this.video = video;
        }
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10,14,24));
        getWindow().setNavigationBarColor(Color.rgb(10,14,24));
        buildScreen();
        connectPlayer();
        askForLibraryPermissions();
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
            handler.postDelayed(() -> playUri(intent.getData(), "Opened media", true), 250);
        handler.post(tick);
    }
    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 10, 12, 8);
        root.setBackgroundColor(Color.rgb(10,14,24));
        setContentView(root);
        TextView brand = text("◉   ORBIT PLAYER", 23);
        brand.setTextColor(Color.rgb(116,185,255));
        root.addView(brand);
        TextView subtitle = text("Music  •  Video  •  Network streams", 13);
        root.addView(subtitle);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams screenParams = new LinearLayout.LayoutParams(-1, 0, 0.9f);
        screenParams.topMargin = 8;
        root.addView(playerView, screenParams);
        now = text("Connect to player…", 13);
        now.setSingleLine(true);
        root.addView(now);
        LinearLayout controls = row();
        playButton = button(controls, "Play", () -> {
            if (player != null) {
                if (player.isPlaying()) player.pause(); else player.play();
            }
        });
        button(controls, "◀◀", () -> { if (player != null) player.seekToPreviousMediaItem(); });
        button(controls, "▶▶", () -> { if (player != null) player.seekToNextMediaItem(); });
        button(controls, "Speed", this::chooseSpeed);
        button(controls, "PiP", this::enterPip);
        LinearLayout options = row();
        shuffleButton = button(options, "Shuffle", () -> {
            if (player != null) { player.setShuffleModeEnabled(!player.getShuffleModeEnabled()); updateModes(); }
        });
        repeatButton = button(options, "Repeat", () -> {
            if (player != null) {
                int mode = player.getRepeatMode();
                player.setRepeatMode(mode == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL :
                    (mode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF));
                updateModes();
            }
        });
        button(options, "Subtitles", this::pickSubtitles);
        button(options, "☆ Favorite", this::toggleFavorite);
        LinearLayout network = row();
        stream = new EditText(this);
        stream.setSingleLine(true);
        stream.setTextColor(Color.WHITE);
        stream.setHintTextColor(0xffaab8c9);
        stream.setTextSize(13);
        stream.setHint("https://... or rtsp://...");
        network.addView(stream, new LinearLayout.LayoutParams(0, -2, 1));
        button(network, "Stream", this::openStream);
        LinearLayout nav = row();
        for (String category : new String[]{"All", "Music", "Video", "Streams", "Favorites"}) {
            button(nav, category, () -> { filter = category; showList(); });
        }
        LinearLayout secondary = row();
        button(secondary, "Open file", this::pickMedia);
        button(secondary, "Rescan", this::scan);
        button(secondary, "Queue", this::showQueue);
        button(secondary, "About", () -> new AlertDialog.Builder(this)
            .setTitle("Orbit Player").setMessage("Open-source Android audio and video player\nPowered by AndroidX Media3.\nVersion 0.1.0 experimental.")
            .setPositiveButton("OK", null).show());
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xffaab8c9);
        search.setTextSize(14);
        search.setHint("Search media library");
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { showList(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(search);
        count = text("No media loaded", 12);
        root.addView(count);
        ScrollView scroll = new ScrollView(this);
        listing = new LinearLayout(this);
        listing.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listing);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.1f));
    }
    private LinearLayout row() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(out);
        return out;
    }
    private TextView text(String content, int size) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(0xffe7eaf2);
        t.setPadding(7, 7, 7, 7);
        return t;
    }
    private Button button(LinearLayout target, String label, Runnable onClick) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(1, 2, 1, 2);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff25364d));
        target.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }
    private void connectPlayer() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        future = new MediaController.Builder(this, token).buildAsync();
        future.addListener(() -> {
            try {
                player = future.get();
                playerView.setPlayer(player);
                player.addListener(new Player.Listener() {
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        toast("Playback error: " + error.getErrorCodeName());
                    }
                });
                updateModes();
                now.setText("Ready. Select a file or enter a stream URL.");
            } catch (Exception e) {
                now.setText("Player initialization failed");
                toast("Could not start media player");
            }
        }, command -> handler.post(command));
    }
    private void askForLibraryPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO}, PERMISSION);
            } else scan();
        } else if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION);
        } else scan();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] p, int[] grants) {
        super.onRequestPermissionsResult(requestCode, p, grants);
        if (requestCode == PERMISSION) scan();
    }
    private void scan() {
        tracks.clear();
        scanType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false);
        scanType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true);
        tracks.sort(Comparator.comparing(e -> e.title.toLowerCase(java.util.Locale.ROOT)));
        showList();
    }
    private void scanType(Uri collection, boolean isVideo) {
        String[] cols = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME};
        try (Cursor c = getContentResolver().query(collection, cols, null, null, null)) {
            if (c == null) return;
            int id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            while (c.moveToNext()) {
                Uri u = android.content.ContentUris.withAppendedId(collection, c.getLong(id));
                tracks.add(new Entry(u, c.getString(name), isVideo));
            }
        } catch (Exception ex) { /* Permission may only cover part of the library */ }
    }
    private void showList() {
        if (listing == null) return;
        listing.removeAllViews();
        displayed.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> favorites = favorites();
        if (filter.equals("Streams")) {
            for (String url : getPreferences(0).getStringSet("streams", Collections.emptySet()))
                displayed.add(new Entry(Uri.parse(url), url, false));
        } else if (filter.equals("Favorites")) {
            for (Entry e : tracks) if (favorites.contains(e.uri.toString())) displayed.add(e);
            for (String url : getPreferences(0).getStringSet("streams", Collections.emptySet()))
                if (favorites.contains(url)) displayed.add(new Entry(Uri.parse(url), url, false));
        } else {
            for (Entry e : tracks) {
                if (filter.equals("Music") && e.video) continue;
                if (filter.equals("Video") && !e.video) continue;
                displayed.add(e);
            }
        }
        ArrayList<Entry> matches = new ArrayList<>();
        for (Entry e : displayed) if (e.title.toLowerCase(java.util.Locale.ROOT).contains(q)) matches.add(e);
        count.setText(matches.size() + " items • " + filter);
        for (Entry e : matches) {
            TextView line = text((favorites.contains(e.uri.toString()) ? "★ " : "") +
                (e.video ? "▶  " : "♫  ") + e.title, 14);
            line.setPadding(12, 14, 12, 14);
            line.setBackgroundColor(0xff182337);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = 3;
            listing.addView(line, lp);
            line.setOnClickListener(v -> playEntry(e));
            line.setOnLongClickListener(v -> { lastUri = e.uri; toggleFavorite(); return true; });
        }
    }
    private void playEntry(Entry entry) {
        lastUri = entry.uri;
        if (player == null) { toast("Player connecting; try again"); return; }
        ArrayList<MediaItem> queue = new ArrayList<>();
        int start = 0;
        for (Entry e : displayed) {
            if (e.uri.equals(entry.uri)) start = queue.size();
            queue.add(new MediaItem.Builder().setUri(e.uri)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(e.title).build()).build());
        }
        if (queue.isEmpty()) return;
        player.setMediaItems(queue, start, 0L);
        player.prepare();
        player.play();
        video = entry.video;
        playerView.setVisibility(video ? View.VISIBLE : View.GONE);
        stream.setText(entry.uri.getScheme() != null && entry.uri.getScheme().startsWith("http") ? entry.uri.toString() : "");
    }
    private void playUri(Uri uri, String title, boolean addHistory) {
        if (player == null) { handler.postDelayed(() -> playUri(uri, title, addHistory), 400); return; }
        lastUri = uri;
        String s = uri.toString().toLowerCase(java.util.Locale.ROOT);
        video = !(s.endsWith(".mp3") || s.endsWith(".m4a") || s.endsWith(".aac") ||
                  s.endsWith(".flac") || s.endsWith(".ogg") || s.endsWith(".wav"));
        player.setMediaItem(new MediaItem.Builder().setUri(uri)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build()).build());
        player.prepare();
        player.play();
        playerView.setVisibility(video ? View.VISIBLE : View.GONE);
    }
    private void openStream() {
        String link = stream.getText().toString().trim();
        if (!(link.startsWith("https://") || link.startsWith("http://") || link.startsWith("rtsp://"))) {
            toast("Use a direct http(s) or rtsp media URL"); return;
        }
        Set<String> set = new HashSet<>(getPreferences(0).getStringSet("streams", Collections.emptySet()));
        set.add(link);
        getPreferences(0).edit().putStringSet("streams", set).apply();
        playUri(Uri.parse(link), link, true);
        if (filter.equals("Streams")) showList();
    }
    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*"});
        startActivityForResult(i, PICK_MEDIA);
    }
    private void pickSubtitles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_SUBTITLE);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        if (requestCode == PICK_MEDIA) playUri(u, "Selected file", false);
        if (requestCode == PICK_SUBTITLE && player != null && player.getCurrentMediaItem() != null) {
            MediaItem original = player.getCurrentMediaItem();
            String path = u.toString().toLowerCase(java.util.Locale.ROOT);
            String mime = path.endsWith(".vtt") ? MimeTypes.TEXT_VTT : MimeTypes.APPLICATION_SUBRIP;
            MediaItem.SubtitleConfiguration sub = new MediaItem.SubtitleConfiguration.Builder(u)
                    .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
            long pos = player.getCurrentPosition();
            boolean wasPlaying = player.isPlaying();
            player.setMediaItem(original.buildUpon()
                .setSubtitleConfigurations(Collections.singletonList(sub)).build());
            player.prepare();
            player.seekTo(Math.max(pos, 0));
            player.setPlayWhenReady(wasPlaying);
        }
    }
    private Set<String> favorites() {
        return getPreferences(0).getStringSet("favorites", Collections.emptySet());
    }
    private void toggleFavorite() {
        if (lastUri == null && player != null && player.getCurrentMediaItem() != null)
            lastUri = player.getCurrentMediaItem().localConfiguration.uri;
        if (lastUri == null) { toast("Choose a media item first"); return; }
        Set<String> set = new HashSet<>(favorites());
        if (!set.add(lastUri.toString())) set.remove(lastUri.toString());
        getPreferences(0).edit().putStringSet("favorites", set).apply();
        showList();
        toast(set.contains(lastUri.toString()) ? "Added to favorites" : "Removed from favorites");
    }
    private void chooseSpeed() {
        if (player == null) return;
        String[] labels = {"0.5×", "0.75×", "1.0×", "1.25×", "1.5×", "2.0×"};
        float[] values = {.5f,.75f,1,1.25f,1.5f,2};
        new AlertDialog.Builder(this).setTitle("Playback speed")
            .setItems(labels, (d, index) -> player.setPlaybackSpeed(values[index])).show();
    }
    private void enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && player != null && video) {
            try { enterPictureInPictureMode(new PictureInPictureParams.Builder().build()); }
            catch (Exception e) { toast("Picture-in-picture unavailable"); }
        } else toast("PiP requires Android 8+ and video playback");
    }
    private void updateModes() {
        if (player == null) return;
        shuffleButton.setText(player.getShuffleModeEnabled() ? "Shuffle ✓" : "Shuffle");
        int mode = player.getRepeatMode();
        repeatButton.setText(mode == Player.REPEAT_MODE_ALL ? "Repeat all" :
                mode == Player.REPEAT_MODE_ONE ? "Repeat one" : "Repeat off");
    }
    private void showQueue() {
        if (player == null) return;
        ArrayList<String> items = new ArrayList<>();
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            CharSequence title = player.getMediaItemAt(i).mediaMetadata.title;
            items.add((i + 1) + ". " + (title == null ? "Media" : title));
        }
        if (items.isEmpty()) { toast("Playback queue is empty"); return; }
        new AlertDialog.Builder(this).setTitle("Playback queue")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    player.seekToDefaultPosition(which); player.play();
                }).show();
    }
    private static String clock(long value) {
        long seconds = Math.max(0, value / 1000);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        playerView.setPlayer(null);
        if (future != null) MediaController.releaseFuture(future);
        super.onDestroy();
    }
}
