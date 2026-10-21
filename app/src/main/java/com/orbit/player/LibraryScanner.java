package com.orbit.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

/** Uses MediaStore for indexed media and SAF for folders users explicitly choose. */
public final class LibraryScanner {
  private static final String TAG="OrbitLibrary";
  private LibraryScanner(){}
  public static ArrayList<MediaEntry> scan(Context context,String kind) {
    boolean audio="audio".equals(kind);
    Uri base=audio?MediaStore.Audio.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    ArrayList<MediaEntry> out=new ArrayList<>();
    ArrayList<String> projection=new ArrayList<>();
    Collections.addAll(projection,MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.TITLE,MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DURATION,MediaStore.MediaColumns.DATE_MODIFIED);
    projection.add(Build.VERSION.SDK_INT>=29?MediaStore.MediaColumns.RELATIVE_PATH:MediaStore.MediaColumns.DATA);
    if(audio){
      projection.add(MediaStore.Audio.Media.ARTIST);
      projection.add(MediaStore.Audio.Media.ALBUM);
    } else {
      projection.add(MediaStore.Video.Media.RESOLUTION);
    }
    try(Cursor c=context.getContentResolver().query(base,projection.toArray(new String[0]),null,null,
        MediaStore.MediaColumns.DATE_ADDED+" DESC")){
      if(c!=null)while(c.moveToNext()){
        long id=c.getLong(0);
        String display=c.getString(1), title=c.getString(2), mime=c.getString(3);
        long duration=c.isNull(4)?0:c.getLong(4);
        long modified=c.isNull(5)?0:c.getLong(5)*1000L;
        String path=c.getString(6);
        String folder=folderOf(path,Build.VERSION.SDK_INT>=29);
        String artist=audio?c.getString(7):"";
        String album=audio?c.getString(8):"";
        if(TextUtils.isEmpty(title))title=display;
        Uri uri=ContentUris.withAppendedId(base,id);
        out.add(new MediaEntry(uri.toString(),title,artist,audio?"audio":"video",
                duration,mime,folder,album,modified));
      }
    }catch(Exception ex){Log.w(TAG,"MediaStore query unavailable for "+kind,ex);}
    return out;
  }
  private static String folderOf(String path,boolean relative){
    if(path==null||path.trim().isEmpty())return "Other";
    String s=path.replace('\\','/');
    if(!relative){
      int i=s.lastIndexOf('/');
      s=i>=0?s.substring(0,i):"Other";
      int storage=s.indexOf("/storage/emulated/0/");
      if(storage>=0)s=s.substring(storage+"/storage/emulated/0/".length());
      else if(s.startsWith("/sdcard/"))s=s.substring(8);
    }
    while(s.endsWith("/"))s=s.substring(0,s.length()-1);
    return s.isEmpty()?"Storage":s;
  }
  private static final class Folder {
    String id,path;
    Folder(String id,String path){this.id=id;this.path=path;}
  }
  private static boolean known(String name,boolean audio){
    if(name==null)return false;
    String s=name.toLowerCase(Locale.ROOT);
    String[] types=audio?new String[]{".mp3",".flac",".wav",".m4a",".aac",".ogg",".opus",".amr",".wma",".aiff",".mid"}
      :new String[]{".mp4",".mkv",".webm",".avi",".mov",".3gp",".m4v",".ts",".mpeg",".mpg",".wmv",".flv"};
    for(String ext:types)if(s.endsWith(ext))return true;
    return false;
  }
  /** Add videos and music in a user-selected tree, including unindexed subfolders. */
  public static void scanTree(Context c,Uri tree,ArrayList<MediaEntry> audio,ArrayList<MediaEntry> video) {
    if(tree==null)return;
    final String rootId;
    try{rootId=DocumentsContract.getTreeDocumentId(tree);}catch(Exception e){return;}
    ArrayDeque<Folder> pending=new ArrayDeque<>();
    String rootName="Selected folder";
    try{
      Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,rootId);
      try(Cursor cr=c.getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
        if(cr!=null&&cr.moveToFirst())rootName=cr.getString(0);
      }
    }catch(Exception ignored){}
    pending.add(new Folder(rootId,rootName));
    HashSet<String> visited=new HashSet<>();
    int scanned=0;
    while(!pending.isEmpty()&&scanned<30000){
      Folder f=pending.removeFirst();
      if(!visited.add(f.id))continue;
      Uri children;
      try{children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,f.id);}
      catch(Exception e){continue;}
      String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,
          DocumentsContract.Document.COLUMN_LAST_MODIFIED};
      try(Cursor cr=c.getContentResolver().query(children,columns,null,null,null)){
        if(cr==null)continue;
        while(cr.moveToNext()&&scanned++<30000){
          String id=cr.getString(0),name=cr.getString(1),mime=cr.getString(2);
          long modified=cr.isNull(3)?0:cr.getLong(3);
          if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
            pending.addLast(new Folder(id,f.path+"/"+name));continue;
          }
          boolean isAudio=(mime!=null&&mime.startsWith("audio/"))||known(name,true);
          boolean isVideo=(mime!=null&&mime.startsWith("video/"))||known(name,false);
          if(!isAudio&&!isVideo)continue;
          Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);
          MediaEntry entry=new MediaEntry(doc.toString(),name,"",isVideo?"video":"audio",0,
              mime==null?"":mime,f.path,"",modified);
          if(isVideo)video.add(entry);else audio.add(entry);
        }
      }catch(Exception ex){Log.w(TAG,"Cannot scan SAF subfolder "+f.path,ex);}
    }
  }
}
