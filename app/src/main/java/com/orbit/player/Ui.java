package com.orbit.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Consistent, accessible design tokens for the Orbit 2 interface. */
final class Ui {
  static final int BG=0xff0b1013, SURFACE=0xff171e22, RAISED=0xff202a2e,
          ACCENT=0xffe6a27c, ACCENT_DIM=0xff362821,
          WHITE=0xfff5f4f0, MUTED=0xffa0acac, LINE=0xff354047, GREEN=0xff86bfaa;
  static int dp(Context c, float v){ return Math.round(c.getResources().getDisplayMetrics().density*v); }
  static GradientDrawable shape(Context c,int color,float radius){
    GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
  }
  static GradientDrawable bordered(Context c,int color,float radius){
    GradientDrawable d=shape(c,color,radius);d.setStroke(dp(c,1),LINE);return d;
  }
  static GradientDrawable hero(Context c) {
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff403027,0xff221d1b,0xff172322});
    d.setCornerRadius(dp(c,23));d.setStroke(dp(c,1),0xff554139);return d;
  }
  static void touch(Context c, View v, int color, int radius) {
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33e6a27c),shape(c,color,radius),null));
    v.setClickable(true);
  }
  static TextView text(Context c,String value,int size,int color,boolean bold){
    TextView t=new TextView(c);t.setText(value);t.setTextColor(color);t.setTextSize(size);
    t.setGravity(Gravity.CENTER_VERTICAL);t.setFontFeatureSettings("kern");
    if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    return t;
  }
  static TextView label(Context c,String s){TextView t=text(c,s,11,MUTED,true);t.setLetterSpacing(.13f);return t;}
  static TextView action(Context c,String s,boolean primary){
    TextView t=text(c,s,13,primary?BG:WHITE,true);t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(c,48));t.setMinWidth(dp(c,48));
    t.setPadding(dp(c,13),dp(c,10),dp(c,13),dp(c,10));
    touch(c,t,primary?ACCENT:RAISED,13);
    return t;
  }
  static TextView icon(Context c,String s){
    TextView t=text(c,s,23,WHITE,false);t.setGravity(Gravity.CENTER);
    t.setMinWidth(dp(c,48));t.setMinHeight(dp(c,48));
    touch(c,t,SURFACE,15);return t;
  }
  static LinearLayout vertical(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
  static LinearLayout horizontal(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  static LinearLayout.LayoutParams lp(Context c,int w,int h,int a,int b,int d,int e){
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(c,w),h<0?h:dp(c,h));
    p.setMargins(dp(c,a),dp(c,b),dp(c,d),dp(c,e));return p;
  }
  static String time(long ms){
    if(ms<=0 || ms==Long.MIN_VALUE||ms==Long.MAX_VALUE) return "--:--";
    long s=ms/1000;
    if(s>=3600)return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",s/3600,(s/60)%60,s%60);
    return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);
  }
}
