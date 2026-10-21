package com.orbit.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** On-device favorites, playlists, network streams and SAF folder grants. */
public final class OrbitStore {
  private final SharedPreferences prefs;
  public OrbitStore(Context c){prefs=c.getSharedPreferences("orbit_store_v1",Context.MODE_PRIVATE);}
  public String tree(){return prefs.getString("folder_uri","");}
  public void setTree(String value){prefs.edit().putString("folder_uri",value).apply();}
  public ArrayList<MediaEntry> streams(){return readEntries(prefs.getString("streams","[]"));}
  public void saveStream(MediaEntry e){
    ArrayList<MediaEntry> s=streams();
    for(java.util.Iterator<MediaEntry> i=s.iterator();i.hasNext();){if(i.next().uri.equals(e.uri))i.remove();}
    s.add(0,e);prefs.edit().putString("streams",encode(s)).apply();
  }
  public void deleteStream(String uri){
    ArrayList<MediaEntry> s=streams();
    for(java.util.Iterator<MediaEntry> i=s.iterator();i.hasNext();){if(i.next().uri.equals(uri))i.remove();}
    prefs.edit().putString("streams",encode(s)).apply();
  }
  public Set<String> favorites(){
    Set<String> s=new HashSet<>();JSONArray arr=parseArray(prefs.getString("favorites","[]"));
    for(int i=0;i<arr.length();i++)s.add(arr.optString(i));return s;
  }
  public boolean toggleFavorite(String uri){
    Set<String> s=favorites();boolean selected;
    if(s.contains(uri)){s.remove(uri);selected=false;}else{s.add(uri);selected=true;}
    prefs.edit().putString("favorites",new JSONArray(s).toString()).apply();return selected;
  }
  public LinkedHashMap<String,ArrayList<MediaEntry>> playlists(){
    LinkedHashMap<String,ArrayList<MediaEntry>> m=new LinkedHashMap<>();
    try {
      JSONObject j=new JSONObject(prefs.getString("playlists","{}"));
      java.util.Iterator<String> k=j.keys();while(k.hasNext()){String key=k.next();m.put(key,readEntries(j.optJSONArray(key)));}
    }catch(JSONException ignored){}
    return m;
  }
  public void createPlaylist(String n){Map<String,ArrayList<MediaEntry>> m=playlists();if(!m.containsKey(n)){m.put(n,new ArrayList<>());save(m);}}
  public void addToPlaylist(String n,MediaEntry e){
    Map<String,ArrayList<MediaEntry>> m=playlists();ArrayList<MediaEntry> a=m.get(n);
    if(a==null){a=new ArrayList<>();m.put(n,a);}
    for(MediaEntry existing:a)if(existing.uri.equals(e.uri))return;
    a.add(e);save(m);
  }
  public void deletePlaylist(String n){Map<String,ArrayList<MediaEntry>> m=playlists();m.remove(n);save(m);}
  private void save(Map<String,ArrayList<MediaEntry>> m){
    JSONObject j=new JSONObject();
    for(Map.Entry<String,ArrayList<MediaEntry>> e:m.entrySet())try{j.put(e.getKey(),arr(e.getValue()));}catch(JSONException ignored){}
    prefs.edit().putString("playlists",j.toString()).apply();
  }
  private static JSONArray arr(List<MediaEntry> a){
    JSONArray j=new JSONArray();for(MediaEntry e:a)try{j.put(e.toJson());}catch(JSONException ignored){}
    return j;
  }
  private static String encode(List<MediaEntry> a){return arr(a).toString();}
  private static JSONArray parseArray(String s){try{return new JSONArray(s);}catch(JSONException e){return new JSONArray();}}
  private static ArrayList<MediaEntry> readEntries(String s){return readEntries(parseArray(s));}
  private static ArrayList<MediaEntry> readEntries(JSONArray a){
    ArrayList<MediaEntry> list=new ArrayList<>();if(a==null)return list;
    for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&!j.optString("uri").isEmpty())list.add(MediaEntry.fromJson(j));}
    return list;
  }
}
