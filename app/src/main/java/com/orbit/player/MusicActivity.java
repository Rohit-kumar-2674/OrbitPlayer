package com.orbit.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;

/** Dedicated full-screen audio transport with large ergonomic controls. */
public final class MusicActivity extends Activity {
  private MediaController player;
  private ListenableFuture<MediaController> future;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private TextView title,artist,play,shuffle,repeat,favorite,elapsed,remaining,cover,queueLabel;
  private SeekBar seek;
  private ImageView albumArt;
  private boolean dragging=false;
  private final Runnable clock=new Runnable(){
    @Override public void run(){updatePosition();handler.postDelayed(this,550);}
  };
  private final Player.Listener listener=new Player.Listener(){
    @Override public void onEvents(Player p,Player.Events e){updateAll();}
    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
      Toast.makeText(MusicActivity.this,"Audio unavailable: "+e.getErrorCodeName(),Toast.LENGTH_LONG).show();
    }
  };
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);
    render();
    future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
    future.addListener(()->{
      try{if(isFinishing()||isDestroyed())return;player=future.get();player.addListener(listener);updateAll();}
      catch(Exception e){Toast.makeText(this,"Audio player unavailable",Toast.LENGTH_LONG).show();}
    },task->handler.post(task));
    handler.post(clock);
  }
  private void render(){
    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(false);
    sc.setBackgroundColor(Ui.BG);setContentView(sc);
    LinearLayout root=Ui.vertical(this);
    root.setPadding(Ui.dp(this,22),Ui.dp(this,12),Ui.dp(this,22),Ui.dp(this,25));sc.addView(root);
    LinearLayout head=Ui.horizontal(this);
    TextView back=Ui.icon(this,"‹");back.setContentDescription("Back to library");back.setOnClickListener(v->finish());
    head.addView(back,Ui.lp(this,50,50,0,0,8,0));
    TextView label=Ui.label(this,"NOW PLAYING");
    label.setGravity(Gravity.CENTER);head.addView(label,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
    TextView options=Ui.icon(this,"⋮");options.setContentDescription("Playback options");options.setOnClickListener(v->moreOptions());
    head.addView(options,Ui.lp(this,50,50,8,0,0,0));
    root.addView(head,Ui.lp(this,-1,52,0,0,0,18));

    LinearLayout visual=Ui.vertical(this);visual.setGravity(Gravity.CENTER);
    visual.setBackground(Ui.hero(this));
    FrameLayout artFrame=new FrameLayout(this);
    TextView icon=Ui.text(this,"◉",120,Ui.ACCENT,true);icon.setGravity(Gravity.CENTER);
    artFrame.addView(icon,new FrameLayout.LayoutParams(-1,-1));
    albumArt=new ImageView(this);albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
    albumArt.setPadding(Ui.dp(this,15),0,Ui.dp(this,15),0);
    artFrame.addView(albumArt,new FrameLayout.LayoutParams(-1,-1));
    visual.addView(artFrame,Ui.lp(this,-1,162,0,0,0,0));
    TextView note=Ui.label(this,"ORBIT  /  AUDIO");
    note.setTextColor(Ui.ACCENT);note.setGravity(Gravity.CENTER);
    visual.addView(note,Ui.lp(this,-1,30,0,0,0,12));
    root.addView(visual,Ui.lp(this,-1,245,0,0,0,27));

    title=Ui.text(this,"Nothing playing",24,Ui.WHITE,true);title.setGravity(Gravity.CENTER);
    title.setMaxLines(2);root.addView(title,Ui.lp(this,-1,66,0,0,0,0));
    artist=Ui.text(this,"Orbit Player",14,Ui.MUTED,false);artist.setGravity(Gravity.CENTER);
    artist.setSingleLine(true);root.addView(artist,Ui.lp(this,-1,35,0,0,0,22));

    seek=new SeekBar(this);seek.setMax(1000);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
    seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onStartTrackingTouch(SeekBar b){dragging=true;}
      public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
        if(fromUser&&player!=null&&player.getDuration()>0)
          elapsed.setText(Ui.time(player.getDuration()*progress/1000));
      }
      public void onStopTrackingTouch(SeekBar b){
        if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*b.getProgress()/1000);
        dragging=false;
      }
    });
    root.addView(seek,Ui.lp(this,-1,42,0,0,0,0));
    LinearLayout times=Ui.horizontal(this);
    elapsed=Ui.text(this,"0:00",12,Ui.MUTED,false);
    remaining=Ui.text(this,"--:--",12,Ui.MUTED,false);
    times.addView(elapsed,new LinearLayout.LayoutParams(0,Ui.dp(this,24),1));times.addView(remaining);
    root.addView(times,Ui.lp(this,-1,26,0,0,0,18));

    LinearLayout transport=Ui.horizontal(this);
    button(transport,"−10",false,57,()->seekBy(-10000));
    button(transport,"|◀",false,60,()->{if(player!=null)player.seekToPreviousMediaItem();});
    play=button(transport,"▶",true,72,()->{if(player!=null){if(player.isPlaying())player.pause();else player.play();}});
    play.setTextSize(26);
    button(transport,"▶|",false,60,()->{if(player!=null)player.seekToNextMediaItem();});
    button(transport,"+10",false,57,()->seekBy(10000));
    root.addView(transport,Ui.lp(this,-1,74,0,0,0,24));

    LinearLayout actions=Ui.horizontal(this);
    shuffle=button(actions,"Shuffle",false,50,()->{if(player!=null){player.setShuffleModeEnabled(!player.getShuffleModeEnabled());updateAll();}});
    favorite=button(actions,"♡ Save",false,50,()->{
      if(player!=null&&player.getCurrentMediaItem()!=null){
        new OrbitStore(this).toggleFavorite(player.getCurrentMediaItem().mediaId);updateAll();
      }
    });
    repeat=button(actions,"Repeat",false,50,()->{
      if(player==null)return;
      int r=player.getRepeatMode();
      player.setRepeatMode(r==Player.REPEAT_MODE_OFF?Player.REPEAT_MODE_ALL:
              r==Player.REPEAT_MODE_ALL?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);
      updateAll();
    });
    root.addView(actions,Ui.lp(this,-1,55,0,0,0,25));
    LinearLayout queueHeader=Ui.horizontal(this);
    TextView queueTitle=Ui.text(this,"Up next",18,Ui.WHITE,true);
    queueHeader.addView(queueTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
    TextView openQueue=Ui.action(this,"View queue  →",false);
    openQueue.setOnClickListener(v->showQueue());queueHeader.addView(openQueue);
    root.addView(queueHeader);
    queueLabel=Ui.text(this,"Playlist empty",12,Ui.MUTED,false);
    root.addView(queueLabel,Ui.lp(this,-1,28,1,0,0,0));
  }
  private TextView button(LinearLayout parent,String s,boolean primary,int height,Runnable action){
    TextView t=Ui.action(this,s,primary);t.setOnClickListener(v->action.run());
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,height),1);
    p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);
    parent.addView(t,p);return t;
  }
  private void seekBy(long offset){
    if(player==null)return;
    long p=Math.max(0,player.getCurrentPosition()+offset),duration=player.getDuration();
    if(duration>0)p=Math.min(duration,p);
    player.seekTo(p);
  }
  private void updateAll(){
    if(player==null)return;
    MediaItem item=player.getCurrentMediaItem();
    title.setText(item!=null&&item.mediaMetadata.title!=null?item.mediaMetadata.title:"Nothing playing");
    if(item!=null&&item.localConfiguration!=null)
      ArtworkLoader.load(this,item.localConfiguration.uri.toString(),false,albumArt);
    artist.setText(item!=null&&item.mediaMetadata.artist!=null?item.mediaMetadata.artist:"Orbit Player");
    play.setText(player.isPlaying()?"Ⅱ":"▶");
    shuffle.setText(player.getShuffleModeEnabled()?"Shuffle ✓":"Shuffle");
    repeat.setText(player.getRepeatMode()==Player.REPEAT_MODE_ONE?"Repeat 1":
       player.getRepeatMode()==Player.REPEAT_MODE_ALL?"Repeat all":"Repeat");
    favorite.setText(item!=null&&new OrbitStore(this).favorites().contains(item.mediaId)?"♥ Saved":"♡ Save");
    queueLabel.setText(player.getMediaItemCount()+" items in playback queue");
    updatePosition();
  }
  private void updatePosition(){
    if(player==null||dragging)return;
    long duration=player.getDuration(),pos=player.getCurrentPosition();
    elapsed.setText(Ui.time(pos));remaining.setText(Ui.time(duration));
    if(duration>0&&duration!=androidx.media3.common.C.TIME_UNSET)
      seek.setProgress((int)Math.min(1000,pos*1000/duration));
  }
  private void moreOptions(){
    new AlertDialog.Builder(this).setTitle("Playback options")
      .setItems(new String[]{"Playback speed","Playback queue","Sleep timer"},(d,which)->{
        if(which==0)speedDialog();
        else if(which==1)showQueue();
        else Toast.makeText(this,"Sleep timer is planned for a future release",Toast.LENGTH_SHORT).show();
      }).show();
  }
  private void speedDialog(){
    if(player==null)return;
    String[] names={"0.5×","0.75×","1.0×","1.25×","1.5×","1.75×","2.0×"};
    float[] rates={.5f,.75f,1,1.25f,1.5f,1.75f,2};
    new AlertDialog.Builder(this).setTitle("Playback speed")
      .setItems(names,(d,i)->player.setPlaybackSpeed(rates[i])).show();
  }
  private void showQueue(){
    if(player==null)return;
    ArrayList<String> items=new ArrayList<>();
    for(int i=0;i<player.getMediaItemCount();i++){
      MediaItem item=player.getMediaItemAt(i);
      items.add((i+1)+". "+(item.mediaMetadata.title==null?"Track":item.mediaMetadata.title));
    }
    if(items.isEmpty()){Toast.makeText(this,"Queue empty",Toast.LENGTH_SHORT).show();return;}
    new AlertDialog.Builder(this).setTitle("Up next").setItems(items.toArray(new String[0]),(d,i)->{
      player.seekToDefaultPosition(i);player.play();
    }).show();
  }
  @Override protected void onDestroy(){
    handler.removeCallbacks(clock);
    if(player!=null)player.removeListener(listener);
    if(future!=null)MediaController.releaseFuture(future);
    super.onDestroy();
  }
}
