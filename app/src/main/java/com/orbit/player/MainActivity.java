package com.orbit.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OrbitPlayer 2: ergonomic multi-section library UI, not an embedded player demo.
 * Main tab navigation is reachable at the bottom, playback stays in a MediaSession.
 */
public final class MainActivity extends Activity {
  private interface TabSelect { void choose(String value); }
  private static final int REQUEST_PERMS=61, REQUEST_FILE=62, REQUEST_FOLDER=63;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ExecutorService background=Executors.newSingleThreadExecutor();
  private final ArrayList<MediaEntry> music=new ArrayList<>(), videos=new ArrayList<>();
  private OrbitStore store;
  private MediaController controller;
  private ListenableFuture<MediaController> future;
  private LinearLayout container,listHost,mini;
  private TextView miniTitle,miniDetail,miniToggle;
  private String section="Home",musicMode="Songs",moreMode="",activeGroup="",query="";
  private int shown=80;
  private boolean scanning=false, permissionGranted=false;
  private MediaEntry pending;
  private final Player.Listener listener=new Player.Listener(){
    @Override public void onEvents(Player p,Player.Events events){updateMini();}
    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
      Toast.makeText(MainActivity.this,"Can't play media: "+e.getErrorCodeName(),Toast.LENGTH_LONG).show();
    }
  };

  @Override public void onCreate(Bundle state){
    super.onCreate(state);getWindow().setStatusBarColor(Ui.BG);
    getWindow().setNavigationBarColor(Ui.BG);
    store=new OrbitStore(this);
    renderShell();
    connect();
    ensurePermission();
    handleOpen(getIntent());
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleOpen(intent);}
  private void handleOpen(Intent i){
    if(i==null||!Intent.ACTION_VIEW.equals(i.getAction())||i.getData()==null)return;
    Uri u=i.getData();
    String mime=i.getType();
    String kind=mime!=null&&mime.startsWith("audio/")?"audio":"video";
    if("http".equals(u.getScheme())||"https".equals(u.getScheme())||"rtsp".equals(u.getScheme()))kind="stream";
    MediaEntry e=new MediaEntry(u.toString(),u.getLastPathSegment(),"Opened externally",kind,0);
    if(controller==null)pending=e;else play(Arrays.asList(e),e);
  }
  private void connect(){
    future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
    future.addListener(()->{
      try{
        if(isFinishing()||isDestroyed())return;
        controller=future.get();controller.addListener(listener);updateMini();
        if(pending!=null){MediaEntry e=pending;pending=null;play(Arrays.asList(e),e);}
      }catch(Exception e){toast("Player connection unavailable");}
    },task->handler.post(task));
  }
  private void ensurePermission(){
    ArrayList<String> missing=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=33){
      if(checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED)
        missing.add(Manifest.permission.READ_MEDIA_AUDIO);
      if(checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED)
        missing.add(Manifest.permission.READ_MEDIA_VIDEO);
    }else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
      missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
    }
    permissionGranted=missing.isEmpty();
    if(permissionGranted)scan();else requestPermissions(missing.toArray(new String[0]),REQUEST_PERMS);
  }
  @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
    super.onRequestPermissionsResult(request,names,results);
    if(request==REQUEST_PERMS){permissionGranted=true;for(int r:results)if(r!=PackageManager.PERMISSION_GRANTED)permissionGranted=false;scan();}
  }
  private void scan(){
    if(scanning)return;
    scanning=true;renderContent();
    background.execute(()->{
      ArrayList<MediaEntry> a=LibraryScanner.scan(this,"audio"),v=LibraryScanner.scan(this,"video");
      String tree=store.tree();
      if(!tree.isEmpty())LibraryScanner.scanTree(this,Uri.parse(tree),a,v);
      java.util.LinkedHashMap<String,MediaEntry> dedup=new java.util.LinkedHashMap<>();
      for(MediaEntry e:a)dedup.put(e.uri,e);a=new ArrayList<>(dedup.values());dedup.clear();
      for(MediaEntry e:v)dedup.put(e.uri,e);v=new ArrayList<>(dedup.values());
      a.sort(Comparator.comparing(e->e.title.toLowerCase(Locale.ROOT)));
      v.sort(Comparator.comparing(e->e.title.toLowerCase(Locale.ROOT)));
      final ArrayList<MediaEntry> allA=a,allV=v;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        music.clear();music.addAll(allA);videos.clear();videos.addAll(allV);
        scanning=false;renderContent();
      });
    });
  }
  private void navigate(String to){
    section=to;moreMode="";activeGroup="";query="";shown=80;
    renderShell();
  }
  private void renderShell(){
    LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);
    setContentView(root);container=root;
    LinearLayout top=Ui.horizontal(this);
    top.setPadding(Ui.dp(this,20),Ui.dp(this,13),Ui.dp(this,16),Ui.dp(this,8));
    TextView emblem=Ui.text(this,"◈",27,Ui.ACCENT,true);emblem.setGravity(Gravity.CENTER);
    emblem.setBackground(Ui.shape(this,Ui.ACCENT_DIM,14));
    top.addView(emblem,Ui.lp(this,46,46,0,0,11,0));
    LinearLayout brand=Ui.vertical(this);
    TextView brandName=Ui.text(this,"ORBIT",20,Ui.WHITE,true);
    brandName.setLetterSpacing(.09f);
    brand.addView(brandName);
    brand.addView(Ui.label(this,"MEDIA, MADE PERSONAL"));
    top.addView(brand,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    TextView settings=Ui.icon(this,"⋮");settings.setContentDescription("More options");
    settings.setOnClickListener(v->navigate("More"));
    top.addView(settings);
    root.addView(top,Ui.lp(this,-1,67,0,0,0,0));

    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
    listHost=Ui.vertical(this);
    listHost.setPadding(Ui.dp(this,18),Ui.dp(this,9),Ui.dp(this,18),Ui.dp(this,15));
    scroll.addView(listHost);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

    mini=Ui.horizontal(this);
    mini.setGravity(Gravity.CENTER_VERTICAL);
    mini.setPadding(Ui.dp(this,10),Ui.dp(this,9),Ui.dp(this,10),Ui.dp(this,9));
    mini.setBackground(Ui.bordered(this,Ui.SURFACE,17));
    TextView art=Ui.text(this,"♫",22,Ui.ACCENT,true);
    art.setGravity(Gravity.CENTER);art.setBackground(Ui.shape(this,Ui.ACCENT_DIM,12));
    mini.addView(art,Ui.lp(this,46,46,0,0,10,0));
    LinearLayout info=Ui.vertical(this);
    miniTitle=Ui.text(this,"Not playing",13,Ui.WHITE,true);
    miniTitle.setSingleLine(true);miniTitle.setEllipsize(TextUtils.TruncateAt.END);
    miniDetail=Ui.text(this,"Tap for player",11,Ui.MUTED,false);
    info.addView(miniTitle);info.addView(miniDetail);
    mini.addView(info,new LinearLayout.LayoutParams(0,Ui.dp(this,45),1));
    info.setOnClickListener(v->openPlayer());
    art.setOnClickListener(v->openPlayer());
    miniToggle=Ui.action(this,"▶",true);
    miniToggle.setContentDescription("Play or pause");
    miniToggle.setOnClickListener(v->{if(controller!=null){if(controller.isPlaying())controller.pause();else controller.play();}});
    mini.addView(miniToggle,Ui.lp(this,48,48,8,0,0,0));
    mini.setVisibility(View.GONE);
    root.addView(mini,Ui.lp(this,-1,66,14,3,14,6));

    LinearLayout nav=Ui.horizontal(this);
    nav.setPadding(Ui.dp(this,5),Ui.dp(this,7),Ui.dp(this,5),Ui.dp(this,8));
    nav.setBackground(Ui.bordered(this,Ui.SURFACE,0));
    String[] destinations={"Home","Music","Videos","Folders","More"};
    String[] symbols={"⌂","♫","▶","▤","☷"};
    for(int i=0;i<destinations.length;i++){
      final String d=destinations[i];
      LinearLayout tile=Ui.vertical(this);
      tile.setGravity(Gravity.CENTER);tile.setMinimumHeight(Ui.dp(this,57));tile.setClickable(true);
      TextView icon=Ui.text(this,symbols[i],22,section.equals(d)?Ui.ACCENT:Ui.MUTED,true);
      icon.setGravity(Gravity.CENTER);
      TextView name=Ui.text(this,d,10,section.equals(d)?Ui.WHITE:Ui.MUTED,section.equals(d));
      name.setGravity(Gravity.CENTER);
      tile.addView(icon);tile.addView(name);
      tile.setContentDescription(d+" tab");tile.setOnClickListener(v->navigate(d));
      nav.addView(tile,new LinearLayout.LayoutParams(0,Ui.dp(this,57),1));
    }
    root.addView(nav,Ui.lp(this,-1,72,0,0,0,0));
    renderContent();updateMini();
  }

  private void sectionHeader(String eyebrow,String title,String desc){
    listHost.addView(Ui.label(this,eyebrow),Ui.lp(this,-1,22,2,0,0,4));
    TextView headline=Ui.text(this,title,28,Ui.WHITE,true);
    listHost.addView(headline,Ui.lp(this,-1,40,0,0,0,1));
    if(desc!=null&&!desc.isEmpty())
      listHost.addView(Ui.text(this,desc,13,Ui.MUTED,false),Ui.lp(this,-1,39,1,0,0,10));
  }
  private void sectionTitle(String s){
    TextView t=Ui.text(this,s,18,Ui.WHITE,true);
    listHost.addView(t,Ui.lp(this,-1,39,2,15,0,8));
  }
  private void renderContent(){
    if(listHost==null)return;
    listHost.removeAllViews();
    switch(section){
      case "Home":showHome();break;
      case "Music":showMusic();break;
      case "Videos":showVideos();break;
      case "Folders":showFolders();break;
      default:showMore();break;
    }
  }
  private void showHome(){
    sectionHeader("YOUR LISTENING SPACE","Good to be back.","A better home for everything you play.");
    LinearLayout hero=Ui.vertical(this);
    hero.setPadding(Ui.dp(this,20),Ui.dp(this,19),Ui.dp(this,20),Ui.dp(this,20));
    hero.setBackground(Ui.hero(this));
    TextView label=Ui.label(this,"ALL YOUR MEDIA. ONE PLACE.");
    label.setTextColor(Ui.ACCENT);hero.addView(label);
    hero.addView(Ui.text(this,"Made for the way\nyou move.",27,Ui.WHITE,true),Ui.lp(this,-1,81,0,8,0,7));
    hero.addView(Ui.text(this,"Offline library, playlists and streaming. No account required.",13,Ui.MUTED,false));
    LinearLayout btns=Ui.horizontal(this);
    TextView musicButton=Ui.action(this,"♫  Play music",true);
    musicButton.setOnClickListener(v->navigate("Music"));
    btns.addView(musicButton,Ui.lp(this,-2,48,0,0,10,0));
    TextView videoButton=Ui.action(this,"▶  Videos",false);
    videoButton.setOnClickListener(v->navigate("Videos"));
    btns.addView(videoButton,Ui.lp(this,-2,48,0,0,0,0));
    hero.addView(btns,Ui.lp(this,-1,48,0,18,0,0));
    listHost.addView(hero,Ui.lp(this,-1,-2,0,3,0,11));

    LinearLayout stats=Ui.horizontal(this);
    stats.addView(stat("♫",String.valueOf(music.size()),"Songs"),new LinearLayout.LayoutParams(0,Ui.dp(this,91),1));
    stats.addView(stat("▶",String.valueOf(videos.size()),"Videos"),Ui.lp(this,0,91,8,0,8,0));
    ((LinearLayout.LayoutParams)stats.getChildAt(1).getLayoutParams()).weight=1;
    stats.addView(stat("▤",String.valueOf(folderGroups().size()),"Folders"),new LinearLayout.LayoutParams(0,Ui.dp(this,91),1));
    listHost.addView(stats,Ui.lp(this,-1,91,0,0,0,4));

    sectionTitle("Explore library");
    LinearLayout actions=Ui.horizontal(this);
    actions.addView(actionCard("▤","Browse folders","Find files by location",()->navigate("Folders")),
        new LinearLayout.LayoutParams(0,Ui.dp(this,123),1));
    actions.addView(actionCard("⌁","Network stream","Open a direct media URL",this::streamDialog),
        Ui.lp(this,0,123,10,0,0,0));
    ((LinearLayout.LayoutParams)actions.getChildAt(1).getLayoutParams()).weight=1;
    listHost.addView(actions);
    sectionTitle("Recently added");
    ArrayList<MediaEntry> recent=allMedia();
    recent.sort((a,b)->Long.compare(b.modified,a.modified));
    if(recent.isEmpty())empty("Your library is waiting","Grant media access or select a whole folder to get started.",true);
    else renderRows(recent.subList(0,Math.min(8,recent.size())),recent,false);
  }
  private LinearLayout stat(String icon,String value,String name){
    LinearLayout card=Ui.vertical(this);card.setPadding(Ui.dp(this,13),Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,8));
    card.setBackground(Ui.bordered(this,Ui.SURFACE,15));
    TextView big=Ui.text(this,icon+"  "+value,21,Ui.WHITE,true);
    card.addView(big);card.addView(Ui.text(this,name,12,Ui.MUTED,false));
    return card;
  }
  private LinearLayout actionCard(String icon,String title,String sub,Runnable click){
    LinearLayout card=Ui.vertical(this);
    card.setPadding(Ui.dp(this,13),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,10));
    Ui.touch(this,card,Ui.SURFACE,16);
    TextView mark=Ui.text(this,icon,25,Ui.ACCENT,true);card.addView(mark,Ui.lp(this,-1,33,0,0,0,3));
    card.addView(Ui.text(this,title,14,Ui.WHITE,true));
    TextView line=Ui.text(this,sub,11,Ui.MUTED,false);
    line.setMaxLines(2);card.addView(line);
    card.setOnClickListener(v->click.run());return card;
  }
  private void searchBox(String hint){
    LinearLayout bar=Ui.horizontal(this);bar.setPadding(Ui.dp(this,14),0,Ui.dp(this,10),0);
    bar.setBackground(Ui.bordered(this,Ui.SURFACE,15));
    TextView icon=Ui.text(this,"⌕",25,Ui.MUTED,false);
    bar.addView(icon,Ui.lp(this,27,52,0,0,7,0));
    EditText field=new EditText(this);
    field.setSingleLine(true);field.setTextColor(Ui.WHITE);field.setHintTextColor(Ui.MUTED);
    field.setTextSize(14);field.setHint(hint);field.setBackgroundColor(Color.TRANSPARENT);
    field.setText(query);field.setPadding(0,0,0,0);
    field.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){
        query=s.toString();shown=80;renderResultsOnly();
      }
      public void afterTextChanged(Editable e){}
    });
    bar.addView(field,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
    listHost.addView(bar,Ui.lp(this,-1,54,0,0,0,11));
  }
  /** Keep keyboard focus intact: update only results below the search field. */
  private LinearLayout resultsHost;
  private void attachResults(){
    resultsHost=Ui.vertical(this);
    listHost.addView(resultsHost);
  }
  private void renderResultsOnly(){
    if(resultsHost==null)return;
    resultsHost.removeAllViews();
    if(section.equals("Music"))renderMusicResults(resultsHost);
    else if(section.equals("Videos"))renderItemList(resultsHost,filtered(videos));
    else if(section.equals("Folders"))renderFolderResults(resultsHost);
    else if(section.equals("More"))renderMoreResults(resultsHost);
  }
  private void chips(String[] modes,String selected,TabSelect onSelect){
    android.widget.HorizontalScrollView outer=new android.widget.HorizontalScrollView(this);
    outer.setHorizontalScrollBarEnabled(false);
    LinearLayout row=Ui.horizontal(this);
    for(String mode:modes){
      TextView chip=Ui.action(this,mode,selected.equals(mode));
      chip.setOnClickListener(v->onSelect.choose(mode));
      row.addView(chip,Ui.lp(this,-2,48,0,0,8,0));
    }
    outer.addView(row);listHost.addView(outer,Ui.lp(this,-1,52,0,0,0,9));
  }
  private void showMusic(){
    sectionHeader("LIBRARY / AUDIO","Your music",music.size()+" songs · Sort, browse, and play.");
    searchBox("Songs, artists and albums");
    chips(new String[]{"Songs","Albums","Artists","Playlists"},musicMode,m->{
      musicMode=m;activeGroup="";shown=80;renderShell();
    });
    attachResults();renderResultsOnly();
  }
  private void renderMusicResults(LinearLayout target){
    if(!activeGroup.isEmpty()){
      backRow(target,activeGroup,()->{activeGroup="";renderResultsOnly();});
      List<MediaEntry> found=new ArrayList<>();
      for(MediaEntry e:music){
        String group=musicMode.equals("Albums")?e.album:e.artist;
        if(activeGroup.equals((group==null||group.isEmpty())?"Unknown "+(musicMode.equals("Albums")?"album":"artist"):group))found.add(e);
      }
      renderItemList(target,filtered(found));return;
    }
    if(musicMode.equals("Songs")){renderItemList(target,filtered(music));return;}
    if(musicMode.equals("Playlists")){renderPlaylists(target);return;}
    LinkedHashMap<String,ArrayList<MediaEntry>> groups=new LinkedHashMap<>();
    for(MediaEntry e:music){
      String group=musicMode.equals("Albums")?e.album:e.artist;
      if(group==null||group.isEmpty())group="Unknown "+(musicMode.equals("Albums")?"album":"artist");
      if(!groups.containsKey(group))groups.put(group,new ArrayList<>());
      groups.get(group).add(e);
    }
    for(String key:sorted(groups.keySet())){
      if(!containsSearch(key))continue;
      final String g=key;
      cardRow(target,musicMode.equals("Albums")?"◈":"♫",g,
          groups.get(key).size()+" tracks",()->{activeGroup=g;renderResultsOnly();},null);
    }
    if(groups.isEmpty())emptyIn(target,"No albums or artists yet","Add local music to build your collection.");
  }
  private void showVideos(){
    sectionHeader("LIBRARY / VIDEO","Your videos",videos.size()+" videos, one beautiful library.");
    searchBox("Search videos and filenames");
    attachResults();renderResultsOnly();
  }
  private ArrayList<MediaEntry> allMedia(){
    ArrayList<MediaEntry> r=new ArrayList<>(music);r.addAll(videos);return r;
  }
  private LinkedHashMap<String,ArrayList<MediaEntry>> folderGroups(){
    LinkedHashMap<String,ArrayList<MediaEntry>> groups=new LinkedHashMap<>();
    for(MediaEntry e:allMedia()){
      String folder=e.folder.isEmpty()?"Other files":e.folder;
      if(!groups.containsKey(folder))groups.put(folder,new ArrayList<>());
      groups.get(folder).add(e);
    }
    return groups;
  }
  private void showFolders(){
    sectionHeader("STORAGE / FOLDERS","Browse folders","Navigate your media by its actual location.");
    LinearLayout actions=Ui.horizontal(this);
    TextView choose=Ui.action(this,"＋ Add whole folder",true);
    choose.setOnClickListener(v->chooseFolder());
    actions.addView(choose,Ui.lp(this,-2,51,0,0,9,0));
    TextView refresh=Ui.action(this,"↻  Rescan",false);
    refresh.setOnClickListener(v->scan());actions.addView(refresh,Ui.lp(this,-2,51,0,0,0,0));
    listHost.addView(actions,Ui.lp(this,-1,54,0,0,0,11));
    String tree=store.tree();
    if(!tree.isEmpty())listHost.addView(Ui.text(this,"✓ Extra folder access enabled · includes subfolders",12,Ui.GREEN,false),Ui.lp(this,-1,31,4,0,0,8));
    searchBox("Filter folders or filenames");
    attachResults();renderResultsOnly();
  }
  private void renderFolderResults(LinearLayout target){
    LinkedHashMap<String,ArrayList<MediaEntry>> groups=folderGroups();
    if(!activeGroup.isEmpty()){
      backRow(target,activeGroup,()->{activeGroup="";renderResultsOnly();});
      renderItemList(target,filtered(groups.getOrDefault(activeGroup,new ArrayList<>())));return;
    }
    ArrayList<String> keys=sorted(groups.keySet());
    for(String key:keys){
      if(!containsSearch(key))continue;
      ArrayList<MediaEntry> a=groups.get(key);int vids=0;
      for(MediaEntry e:a)if(e.isVideo())vids++;
      int songs=a.size()-vids;
      final String folder=key;
      cardRow(target,"▤",key,songs+" songs  ·  "+vids+" videos",
          ()->{activeGroup=folder;renderResultsOnly();},null);
    }
    if(keys.isEmpty())emptyIn(target,"No folders found",
      "Allow access to music and videos, or tap Add whole folder to scan nested directories.");
  }
  private void showMore(){
    sectionHeader("TOOLS / COLLECTIONS","Your space","Playlists, streams and useful actions.");
    if(!moreMode.isEmpty()){
      backRow(listHost,moreMode,()->{moreMode="";renderShell();});
      if(moreMode.equals("Streams")){
        TextView add=Ui.action(this,"＋ New network stream",true);
        add.setOnClickListener(v->streamDialog());
        listHost.addView(add,Ui.lp(this,-1,52,0,6,0,14));
      }
      if(moreMode.equals("Playlists")){
        TextView add=Ui.action(this,"＋ New playlist",true);add.setOnClickListener(v->createPlaylist(null));
        listHost.addView(add,Ui.lp(this,-1,52,0,6,0,14));
      }
      attachResults();renderMoreResults(resultsHost);return;
    }
    cardRow(listHost,"⌁","Network streams","HLS, DASH, RTSP and direct URLs",()->{moreMode="Streams";renderShell();},null);
    cardRow(listHost,"♡","Favorites","Your saved music and videos",()->{moreMode="Favorites";renderShell();},null);
    cardRow(listHost,"▥","Playlists","Curate your listening",()->{moreMode="Playlists";renderShell();},null);
    cardRow(listHost,"▤","Storage access","Pick an entire folder, including subfolders",this::chooseFolder,null);
    cardRow(listHost,"↻","Rescan library","Refresh audio, video and folders",this::scan,null);
    cardRow(listHost,"⊕","Open a media file","Use the system file browser",this::pickFile,null);
    cardRow(listHost,"ⓘ","About Orbit","Open-source, local-first media player",this::about,null);
  }
  private void renderMoreResults(LinearLayout target){
    switch(moreMode){
      case "Streams":renderItemList(target,store.streams());break;
      case "Favorites":
        ArrayList<MediaEntry> fav=new ArrayList<>();
        Set<String> liked=store.favorites();
        for(MediaEntry e:allMedia())if(liked.contains(e.uri))fav.add(e);
        for(MediaEntry e:store.streams())if(liked.contains(e.uri))fav.add(e);
        renderItemList(target,fav);break;
      case "Playlists":renderPlaylists(target);break;
    }
  }
  private void renderPlaylists(LinearLayout target){
    LinkedHashMap<String,ArrayList<MediaEntry>> lists=store.playlists();
    if(!activeGroup.isEmpty()){
      backRow(target,activeGroup,()->{activeGroup="";renderResultsOnly();});
      renderItemList(target,lists.getOrDefault(activeGroup,new ArrayList<>()));return;
    }
    for(String name:sorted(lists.keySet())){
      final String selected=name;
      cardRow(target,"▥",name,lists.get(name).size()+" tracks",
          ()->{activeGroup=selected;renderResultsOnly();},
          ()->new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Delete playlist"},(d,n)->
            new AlertDialog.Builder(this).setMessage("Delete "+name+"?").setPositiveButton("Delete",(dialog,i)->{
              store.deletePlaylist(name);renderResultsOnly();
            }).setNegativeButton("Cancel",null).show()).show());
    }
    if(lists.isEmpty())emptyIn(target,"Create your first playlist","Use New playlist, then add songs with their ••• menu.");
  }
  private void backRow(LinearLayout host,String label,Runnable onBack){
    TextView back=Ui.action(this,"‹  "+label,false);
    back.setGravity(Gravity.CENTER_VERTICAL);
    back.setOnClickListener(v->onBack.run());
    host.addView(back,Ui.lp(this,-1,52,0,2,0,10));
  }
  private boolean containsSearch(String value){
    return query.trim().isEmpty()||value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
  }
  private ArrayList<MediaEntry> filtered(Collection<MediaEntry> entries){
    ArrayList<MediaEntry> result=new ArrayList<>();
    for(MediaEntry e:entries)
      if(containsSearch(e.title)||containsSearch(e.artist)||containsSearch(e.album)||containsSearch(e.folder))result.add(e);
    return result;
  }
  private ArrayList<String> sorted(Collection<String> keys){
    ArrayList<String> list=new ArrayList<>(keys);list.sort(String.CASE_INSENSITIVE_ORDER);return list;
  }
  private void renderRows(List<MediaEntry> subset,List<MediaEntry> queue,boolean limit){
    for(MediaEntry e:subset)cardRow(listHost,e.isVideo()?"▶":"♫",e.title,
        mediaMeta(e),()->play(queue,e),()->itemMenu(e));
  }
  private void renderItemList(LinearLayout target,List<MediaEntry> entries){
    TextView count=Ui.text(this,entries.size()+" ITEMS"+(scanning?"  ·  SCANNING…":""),11,Ui.MUTED,true);
    count.setLetterSpacing(.12f);
    target.addView(count,Ui.lp(this,-1,34,4,0,0,7));
    ArrayList<MediaEntry> queue=new ArrayList<>(entries);
    int end=Math.min(shown,entries.size());
    for(int i=0;i<end;i++){
      MediaEntry e=entries.get(i);
      cardRow(target,e.isVideo()?"▶":"♫",e.title,mediaMeta(e),
          ()->play(queue,e),()->itemMenu(e));
    }
    if(end<entries.size()){
      TextView more=Ui.action(this,"Show more  ("+(entries.size()-end)+" remaining)",false);
      more.setOnClickListener(v->{shown+=80;renderResultsOnly();});
      target.addView(more,Ui.lp(this,-1,52,0,12,0,8));
    }
    if(entries.isEmpty())emptyIn(target,"Nothing here yet","Use the storage permission or Add whole folder to discover your media.");
  }
  private String mediaMeta(MediaEntry e){
    String sub=!e.artist.isEmpty()?e.artist:e.folder;
    if(sub.isEmpty())sub=e.kind.equals("stream")?"NETWORK STREAM":"LOCAL MEDIA";
    if(e.duration>0)sub+="  ·  "+Ui.time(e.duration);
    return sub;
  }
  private void cardRow(LinearLayout host,String symbol,String title,String meta,Runnable onOpen,Runnable onMenu){
    LinearLayout row=Ui.horizontal(this);
    row.setPadding(Ui.dp(this,11),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10));
    Ui.touch(this,row,Ui.SURFACE,16);row.setMinimumHeight(Ui.dp(this,76));
    TextView badge=Ui.text(this,symbol,23,Ui.ACCENT,true);
    badge.setGravity(Gravity.CENTER);badge.setBackground(Ui.shape(this,Ui.RAISED,13));
    row.addView(badge,Ui.lp(this,50,52,0,0,12,0));
    LinearLayout content=Ui.vertical(this);
    TextView heading=Ui.text(this,title,14,Ui.WHITE,true);
    heading.setMaxLines(1);heading.setEllipsize(TextUtils.TruncateAt.END);
    content.addView(heading);
    TextView subtitle=Ui.text(this,meta,11,Ui.MUTED,false);
    subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);
    content.addView(subtitle,Ui.lp(this,-1,23,0,2,0,0));
    row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
    if(onMenu!=null){
      TextView menu=Ui.icon(this,"⋮");
      menu.setContentDescription("Options for "+title);
      menu.setOnClickListener(v->onMenu.run());
      row.addView(menu,Ui.lp(this,48,48,4,0,0,0));
    }else{
      TextView chevron=Ui.text(this,"›",29,Ui.MUTED,false);
      chevron.setGravity(Gravity.CENTER);
      row.addView(chevron,Ui.lp(this,36,48,0,0,0,0));
    }
    row.setOnClickListener(v->onOpen.run());
    if(onMenu!=null)row.setOnLongClickListener(v->{onMenu.run();return true;});
    host.addView(row,Ui.lp(this,-1,-2,0,0,0,8));
  }
  private void emptyIn(LinearLayout target,String title,String msg){
    LinearLayout box=Ui.vertical(this);box.setGravity(Gravity.CENTER);
    box.setPadding(Ui.dp(this,22),Ui.dp(this,35),Ui.dp(this,22),Ui.dp(this,40));
    box.setBackground(Ui.bordered(this,Ui.SURFACE,18));
    TextView art=Ui.text(this,"◈",43,Ui.ACCENT,true);art.setGravity(Gravity.CENTER);box.addView(art);
    TextView big=Ui.text(this,title,17,Ui.WHITE,true);big.setGravity(Gravity.CENTER);box.addView(big);
    TextView sub=Ui.text(this,msg,13,Ui.MUTED,false);sub.setGravity(Gravity.CENTER);
    box.addView(sub,Ui.lp(this,-1,-2,0,9,0,0));
    target.addView(box,Ui.lp(this,-1,-2,0,12,0,0));
  }
  private void empty(String title,String msg,boolean action){
    emptyIn(listHost,title,msg);
    if(action){
      TextView pick=Ui.action(this,"Add a whole folder",true);
      pick.setOnClickListener(v->chooseFolder());
      listHost.addView(pick,Ui.lp(this,-1,52,0,14,0,12));
    }
  }
  private void play(List<MediaEntry> queue,MediaEntry chosen){
    if(controller==null){pending=chosen;toast("Connecting player…");return;}
    ArrayList<MediaItem> items=new ArrayList<>();
    int selected=0;
    for(int i=0;i<queue.size();i++){
      MediaEntry e=queue.get(i);items.add(e.toItem());if(e.uri.equals(chosen.uri))selected=i;
    }
    if(items.isEmpty())items.add(chosen.toItem());
    controller.setMediaItems(items,selected,0);
    controller.prepare();controller.play();
    startActivity(new Intent(this,chosen.isVideo()?VideoActivity.class:MusicActivity.class));
    updateMini();
  }
  private void updateMini(){
    if(mini==null)return;
    if(controller==null||controller.getCurrentMediaItem()==null){
      mini.setVisibility(View.GONE);return;
    }
    MediaItem item=controller.getCurrentMediaItem();mini.setVisibility(View.VISIBLE);
    miniTitle.setText(item.mediaMetadata.title!=null?item.mediaMetadata.title:"Now playing");
    miniDetail.setText(controller.isPlaying()?"NOW PLAYING  ·  TAP TO EXPAND":"PAUSED  ·  TAP TO EXPAND");
    miniToggle.setText(controller.isPlaying()?"Ⅱ":"▶");
  }
  private void openPlayer(){
    if(controller==null||controller.getCurrentMediaItem()==null)return;
    String uri=controller.getCurrentMediaItem().mediaId;
    boolean video=true;
    for(MediaEntry e:allMedia())if(e.uri.equals(uri)){video=e.isVideo();break;}
    if(uri.endsWith(".mp3")||uri.endsWith(".flac")||uri.endsWith(".m4a"))video=false;
    startActivity(new Intent(this,video?VideoActivity.class:MusicActivity.class));
  }
  private void itemMenu(MediaEntry e){
    boolean liked=store.favorites().contains(e.uri);
    ArrayList<String> options=new ArrayList<>(Arrays.asList(liked?"Remove favorite":"Add favorite","Add to playlist","Play next","Share media"));
    if(e.kind.equals("stream"))options.add("Remove saved stream");
    new AlertDialog.Builder(this).setTitle(e.title).setItems(options.toArray(new String[0]),(dialog,index)->{
      switch(index){
        case 0:store.toggleFavorite(e.uri);renderContent();break;
        case 1:choosePlaylist(e);break;
        case 2:if(controller!=null)controller.addMediaItem(
                Math.min(Math.max(controller.getCurrentMediaItemIndex()+1,0),controller.getMediaItemCount()),e.toItem());
               toast("Added to next in queue");break;
        case 3:
          Intent send=new Intent(Intent.ACTION_SEND);
          if(e.uri.startsWith("content://")){
            send.setType(e.isVideo()?"video/*":"audio/*");
            send.putExtra(Intent.EXTRA_STREAM,Uri.parse(e.uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          }else{send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,e.uri);}
          startActivity(Intent.createChooser(send,"Share media"));break;
        case 4:store.deleteStream(e.uri);renderContent();break;
      }
    }).show();
  }
  private EditText field(String hint){
    EditText f=new EditText(this);f.setSingleLine(true);f.setTextColor(Ui.WHITE);
    f.setHintTextColor(Ui.MUTED);f.setHint(hint);return f;
  }
  private void choosePlaylist(MediaEntry e){
    ArrayList<String> names=new ArrayList<>(store.playlists().keySet());
    names.add("＋ Create new playlist");
    new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]),(d,index)->{
      if(index==names.size()-1)createPlaylist(e);
      else{store.addToPlaylist(names.get(index),e);toast("Added to "+names.get(index));}
    }).show();
  }
  private void createPlaylist(MediaEntry chosen){
    EditText name=field("Playlist name");
    LinearLayout wrap=Ui.vertical(this);wrap.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);wrap.addView(name);
    new AlertDialog.Builder(this).setTitle("New playlist").setView(wrap).setNegativeButton("Cancel",null)
      .setPositiveButton("Create",(d,n)->{
        String value=name.getText().toString().trim();
        if(value.isEmpty())return;
        store.createPlaylist(value);if(chosen!=null)store.addToPlaylist(value,chosen);
        renderContent();
      }).show();
  }
  private void streamDialog(){
    EditText name=field("Stream title (optional)"),url=field("https://… or rtsp://…");
    LinearLayout wrap=Ui.vertical(this);wrap.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
    wrap.addView(name);wrap.addView(url);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Open network stream")
      .setView(wrap).setNegativeButton("Cancel",null).setPositiveButton("Save & play",null).create();
    dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      String raw=url.getText().toString().trim(),scheme=Uri.parse(raw).getScheme();
      if(scheme==null||!(scheme.equals("http")||scheme.equals("https")||scheme.equals("rtsp"))
            ||Uri.parse(raw).getHost()==null){url.setError("Enter a direct HTTP(S) or RTSP media URL");return;}
      String title=name.getText().toString().trim();
      if(title.isEmpty())title=Uri.parse(raw).getHost();
      String mime="";
      if(raw.toLowerCase(Locale.ROOT).contains(".m3u8"))mime=MimeTypes.APPLICATION_M3U8;
      if(raw.toLowerCase(Locale.ROOT).contains(".mpd"))mime=MimeTypes.APPLICATION_MPD;
      MediaEntry entry=new MediaEntry(raw,title,"Network stream","stream",0,mime);
      store.saveStream(entry);dialog.dismiss();play(Arrays.asList(entry),entry);renderContent();
    }));
    dialog.show();
  }
  private void pickFile(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*","application/octet-stream"});
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,REQUEST_FILE);
  }
  private void chooseFolder(){
    if(Build.VERSION.SDK_INT<21){pickFile();return;}
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,REQUEST_FOLDER);
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    try{
      getContentResolver().takePersistableUriPermission(uri,
              data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }catch(SecurityException ignored){}
    if(req==REQUEST_FOLDER){
      store.setTree(uri.toString());section="Folders";activeGroup="";
      renderShell();scan();return;
    }
    if(req==REQUEST_FILE){
      String title=uri.getLastPathSegment(),mime=getContentResolver().getType(uri);
      try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
        if(c!=null&&c.moveToFirst())title=c.getString(0);
      }catch(Exception ignored){}
      String kind=mime!=null&&mime.startsWith("audio/")?"audio":"video";
      play(Arrays.asList(new MediaEntry(uri.toString(),title,"Selected file",kind,0)),
          new MediaEntry(uri.toString(),title,"Selected file",kind,0));
    }
  }
  private void about(){
    new AlertDialog.Builder(this).setTitle("Orbit Player 2").setMessage(
      "An independent, open-source, local-first media player.\n\n" +
      "Designed for touch: 48dp minimum controls, persistent bottom navigation and native folder access.\n\n" +
      "Playback powered by AndroidX Media3. No account or analytics.")
      .setPositiveButton("Close",null).show();
  }
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  @Override protected void onResume(){super.onResume();updateMini();}
  @Override public void onBackPressed(){
    if(!activeGroup.isEmpty()){activeGroup="";renderContent();}
    else if(!moreMode.isEmpty()){moreMode="";renderShell();}
    else if(!section.equals("Home"))navigate("Home");
    else super.onBackPressed();
  }
  @Override protected void onDestroy(){
    background.shutdownNow();
    if(controller!=null)controller.removeListener(listener);
    if(future!=null)MediaController.releaseFuture(future);
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
