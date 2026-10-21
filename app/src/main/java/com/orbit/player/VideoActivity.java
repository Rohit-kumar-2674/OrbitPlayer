package com.orbit.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.Locale;

/** Clean, focused video playback with expandable controls and touch-sized actions. */
public final class VideoActivity extends Activity {
  private static final int REQUEST_CAPTIONS=301;
  private MediaController player;
  private ListenableFuture<MediaController> future;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private PlayerView screen;
  private LinearLayout bar,actions;
  private TextView title;
  private int resize=0;
  private final Player.Listener listener=new Player.Listener(){
    @Override public void onEvents(Player p,Player.Events e){
      MediaItem item=p.getCurrentMediaItem();
      if(item!=null&&title!=null&&item.mediaMetadata.title!=null)title.setText(item.mediaMetadata.title);
      screen.setKeepScreenOn(p.isPlaying());
    }
    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
      new AlertDialog.Builder(VideoActivity.this).setTitle("Unable to play")
         .setMessage("This file or stream may need an unsupported codec.\n\n"+e.getErrorCodeName())
         .setPositiveButton("OK",null).show();
    }
  };
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().setNavigationBarColor(Ui.BG);
    render();
    future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
    future.addListener(()->{
      try{
        if(isFinishing()||isDestroyed())return;
        player=future.get();player.addListener(listener);
        screen.setPlayer(player);
        MediaItem item=player.getCurrentMediaItem();
        if(item!=null&&item.mediaMetadata.title!=null)title.setText(item.mediaMetadata.title);
      }catch(Exception e){Toast.makeText(this,"Video player unavailable",Toast.LENGTH_LONG).show();}
    },task->handler.post(task));
  }
  private void render(){
    LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);setContentView(root);
    bar=Ui.horizontal(this);
    bar.setPadding(Ui.dp(this,12),Ui.dp(this,7),Ui.dp(this,12),Ui.dp(this,8));
    TextView back=Ui.icon(this,"‹");back.setContentDescription("Back to library");back.setOnClickListener(v->finish());
    bar.addView(back,Ui.lp(this,50,50,0,0,8,0));
    title=Ui.text(this,"Orbit Player",15,Ui.WHITE,true);
    title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    bar.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
    TextView pip=Ui.action(this,"PiP",false);pip.setOnClickListener(v->enterPip());
    bar.addView(pip,Ui.lp(this,55,50,8,0,0,0));
    root.addView(bar,Ui.lp(this,-1,65,0,0,0,0));

    screen=new PlayerView(this);
    screen.setUseController(true);
    screen.setControllerShowTimeoutMs(3500);
    screen.setBackgroundColor(android.graphics.Color.BLACK);
    screen.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    screen.setShowSubtitleButton(true);
    root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));

    actions=Ui.vertical(this);
    actions.setPadding(Ui.dp(this,12),Ui.dp(this,13),Ui.dp(this,12),Ui.dp(this,14));
    actions.setBackground(Ui.bordered(this,Ui.SURFACE,0));
    LinearLayout transport=Ui.horizontal(this);
    control(transport,"−10s",()->seekBy(-10000));
    control(transport,"+10s",()->seekBy(10000));
    control(transport,"Speed",this::chooseSpeed);
    control(transport,"Resize",this::cycleAspect);
    actions.addView(transport,Ui.lp(this,-1,52,0,0,0,9));
    LinearLayout other=Ui.horizontal(this);
    control(other,"Subtitles",this::pickSubtitles);
    control(other,"Rotation",this::rotate);
    control(other,"Tracks",this::trackHelp);
    control(other,"Queue",this::queue);
    actions.addView(other,Ui.lp(this,-1,52,0,0,0,0));
    root.addView(actions,Ui.lp(this,-1,-2,0,0,0,0));
  }
  private void control(LinearLayout host,String text,Runnable r){
    TextView t=Ui.action(this,text,false);
    t.setOnClickListener(v->r.run());
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);
    p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);
    host.addView(t,p);
  }
  private void seekBy(long delta){
    if(player==null)return;
    long d=player.getDuration(),p=Math.max(0,player.getCurrentPosition()+delta);
    if(d>0&&d!=C.TIME_UNSET)p=Math.min(d,p);
    player.seekTo(p);
  }
  private void chooseSpeed(){
    if(player==null)return;
    String[] names={".5×",".75×","1×","1.25×","1.5×","1.75×","2×"};
    float[] rates={.5f,.75f,1f,1.25f,1.5f,1.75f,2f};
    new AlertDialog.Builder(this).setTitle("Video speed").setItems(names,(d,i)->player.setPlaybackSpeed(rates[i])).show();
  }
  private void cycleAspect(){
    resize=(resize+1)%3;
    int[] sizes={AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                  AspectRatioFrameLayout.RESIZE_MODE_FILL};
    screen.setResizeMode(sizes[resize]);
    Toast.makeText(this,new String[]{"Fit","Zoom","Fill"}[resize],Toast.LENGTH_SHORT).show();
  }
  private void rotate(){
    boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
    setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
  }
  private void pickSubtitles(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,REQUEST_CAPTIONS);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=REQUEST_CAPTIONS||result!=RESULT_OK||data==null||data.getData()==null||player==null)return;
    Uri uri=data.getData();
    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    catch(SecurityException ignored){}
    MediaItem item=player.getCurrentMediaItem();if(item==null)return;
    String path=uri.getLastPathSegment()==null?"":uri.getLastPathSegment().toLowerCase(Locale.ROOT);
    String mime=path.endsWith(".vtt")?MimeTypes.TEXT_VTT:
         (path.endsWith(".ssa")||path.endsWith(".ass"))?MimeTypes.TEXT_SSA:MimeTypes.APPLICATION_SUBRIP;
    MediaItem.SubtitleConfiguration sub=new MediaItem.SubtitleConfiguration.Builder(uri)
         .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
    MediaItem update=item.buildUpon().setSubtitleConfigurations(Collections.singletonList(sub)).build();
    long pos=player.getCurrentPosition();int index=player.getCurrentMediaItemIndex();
    boolean wasPlaying=player.getPlayWhenReady();
    player.replaceMediaItem(index,update);
    player.seekTo(index,pos);player.setPlayWhenReady(wasPlaying);
    Toast.makeText(this,"Subtitles added",Toast.LENGTH_SHORT).show();
  }
  private void trackHelp(){
    new AlertDialog.Builder(this).setTitle("Audio & subtitle tracks").setMessage(
        "Tap the playback screen and use its track selector for embedded audio/subtitles. Or choose Subtitles here to load an external SRT, VTT or ASS file.")
      .setPositiveButton("OK",null).show();
  }
  private void queue(){
    if(player==null)return;
    String[] names=new String[player.getMediaItemCount()];
    for(int i=0;i<names.length;i++){
      CharSequence t=player.getMediaItemAt(i).mediaMetadata.title;
      names[i]=(i+1)+". "+(t==null?"Video":t);
    }
    new AlertDialog.Builder(this).setTitle("Up next").setItems(names,(d,i)->{
      player.seekToDefaultPosition(i);player.play();
    }).show();
  }
  private void enterPip(){
    if(Build.VERSION.SDK_INT<26||player==null)return;
    try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}
    catch(Exception e){Toast.makeText(this,"PiP unavailable",Toast.LENGTH_SHORT).show();}
  }
  @Override public void onPictureInPictureModeChanged(boolean active,Configuration conf){
    super.onPictureInPictureModeChanged(active,conf);
    bar.setVisibility(active?View.GONE:View.VISIBLE);
    actions.setVisibility(active?View.GONE:View.VISIBLE);
    screen.setUseController(!active);
  }
  @Override protected void onDestroy(){
    screen.setPlayer(null);
    if(player!=null)player.removeListener(listener);
    if(future!=null)MediaController.releaseFuture(future);
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
