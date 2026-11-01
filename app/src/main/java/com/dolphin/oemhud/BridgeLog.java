package com.dolphin.oemhud;

import android.content.*;
import java.text.*;
import java.util.*;

public final class BridgeLog {
    static final String STORE="nav_bridge";
    private BridgeLog() {}
    public static boolean isNavPackage(Context context, String name){
        if(name==null)return false;
        String chosen=context.getSharedPreferences(STORE,0).getString("nav_package","").trim();
        if(!chosen.isEmpty())return chosen.equals(name);
        String s=name.toLowerCase(Locale.ROOT);
        return s.contains("nav")||s.contains("tmap")||s.contains("autonavi")||s.contains("amap")||s.contains("navi");
    }
    public static synchronized void event(Context ctx,String line){
        String stamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.KOREA).format(new Date());
        android.content.SharedPreferences p=ctx.getSharedPreferences(STORE,0);
        String old=p.getString("history","");
        String item=stamp+" "+line.replace('\n',' ').replace('\r',' ');
        if(item.length()>300)item=item.substring(0,300);
        String combined=old+item+"\n";
        if(combined.length()>15000)combined=combined.substring(combined.length()-15000);
        p.edit().putString("history",combined).apply();
        android.util.Log.i("DolphinHud",item);
    }
    public static void cue(Context ctx,NavCue cue){
        if(cue==null || !ctx.getSharedPreferences(STORE,0).getBoolean("enabled",false))return;
        ctx.getSharedPreferences(STORE,0).edit().putString("last_cue",cue.display()).apply();
        event(ctx,"TBT "+cue.display());
        if(cue.isUsable())BridgeService.onCue(cue);
        else event(ctx,"TBT 방향·거리 불확실 → HUD 명령 생성 생략 (원본 확인 필요)");
    }
}
