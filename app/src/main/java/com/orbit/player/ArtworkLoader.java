package com.orbit.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded asynchronous embedded-cover / video-preview loader. Never blocks scrolling. */
final class ArtworkLoader {
  private static final ExecutorService pool=Executors.newFixedThreadPool(2);
  private static final Handler main=new Handler(Looper.getMainLooper());
  private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(18*1024){
    @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount()/1024;}
  };
  static void load(Context c,String uri,boolean video,ImageView view){
    if(uri==null||uri.isEmpty())return;
    if(uri.equals(view.getTag()))return;
    view.setTag(uri);
    view.setImageDrawable(null);
    Bitmap existing=cache.get(uri);
    if(existing!=null){view.setImageBitmap(existing);return;}
    Context context=c.getApplicationContext();
    pool.execute(()->{
      Bitmap result=null;
      MediaMetadataRetriever mmr=new MediaMetadataRetriever();
      try{
        mmr.setDataSource(context,Uri.parse(uri));
        if(video)result=mmr.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        else {
          byte[] embedded=mmr.getEmbeddedPicture();
          if(embedded!=null){
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(embedded,0,embedded.length,o);
            o.inSampleSize=Math.max(1,Math.max(o.outWidth,o.outHeight)/320);
            o.inJustDecodeBounds=false;
            result=BitmapFactory.decodeByteArray(embedded,0,embedded.length,o);
          }
        }
        if(result!=null){
          Bitmap small=Bitmap.createScaledBitmap(result,192,192,true);
          if(small!=result)result.recycle();
          result=small;
          cache.put(uri,result);
        }
      }catch(Exception|OutOfMemoryError ignored){}finally{
        try{mmr.release();}catch(Exception ignored){}
      }
      final Bitmap finalBitmap=result;
      main.post(()->{
        if(uri.equals(view.getTag())&&finalBitmap!=null)view.setImageBitmap(finalBitmap);
      });
    });
  }
}
