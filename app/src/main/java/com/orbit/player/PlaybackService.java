package com.orbit.player;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public final class PlaybackService extends MediaSessionService {
  private ExoPlayer player;
  private MediaSession session;
  @Override public void onCreate() {
    super.onCreate();
    player = new ExoPlayer.Builder(this).build();
    Intent i = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    session = new MediaSession.Builder(this, player).setSessionActivity(pi).build();
  }
  @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return session; }
  @Override public void onDestroy() {
    if (session != null) { session.release(); session = null; }
    if (player != null) { player.release(); player = null; }
    super.onDestroy();
  }
}
