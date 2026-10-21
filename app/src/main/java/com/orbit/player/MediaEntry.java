package com.orbit.player;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Locale;

/** Device or Storage Access Framework media record; no direct filesystem access required. */
public final class MediaEntry {
  public final String uri, title, artist, kind, mime, folder, album;
  public final long duration, modified;
  public MediaEntry(String uri,String title,String artist,String kind,long duration){
    this(uri,title,artist,kind,duration,"","","",0);
  }
  public MediaEntry(String uri,String title,String artist,String kind,long duration,String mime){
    this(uri,title,artist,kind,duration,mime,"","",0);
  }
  public MediaEntry(String uri,String title,String artist,String kind,long duration,String mime,String folder,String album,long modified){
    this.uri=uri;this.title=(title==null||title.trim().isEmpty())?"Untitled":title;
    this.artist=artist==null?"":artist;this.kind=kind==null?"video":kind;
    this.duration=duration;this.mime=mime==null?"":mime;
    this.folder=folder==null?"":folder;this.album=album==null?"":album;this.modified=modified;
  }
  public boolean isVideo(){return "video".equals(kind)||"stream".equals(kind);}
  public MediaItem toItem(){
    MediaMetadata m=new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build();
    MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(uri)).setMediaId(uri).setMediaMetadata(m);
    String l=uri.toLowerCase(Locale.ROOT);
    if(!mime.isEmpty())b.setMimeType(mime);
    else if(l.contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
    else if(l.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
    return b.build();
  }
  public JSONObject toJson() throws JSONException {
    JSONObject o=new JSONObject();o.put("uri",uri);o.put("title",title);o.put("artist",artist);
    o.put("kind",kind);o.put("duration",duration);o.put("mime",mime);o.put("folder",folder);
    o.put("album",album);o.put("modified",modified);return o;
  }
  public static MediaEntry fromJson(JSONObject o){
    return new MediaEntry(o.optString("uri"),o.optString("title"),o.optString("artist"),
            o.optString("kind","video"),o.optLong("duration"),o.optString("mime"),
            o.optString("folder"),o.optString("album"),o.optLong("modified"));
  }
}
