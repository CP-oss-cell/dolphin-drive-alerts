package com.dolphin.oemhud;

import android.content.*;
import android.os.Build;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!c.getSharedPreferences(BridgeLog.STORE,0).getBoolean("enabled",false))return;
        Intent s=new Intent(c,BridgeService.class);
        try{
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
        }catch(RuntimeException e){
            BridgeLog.event(c,"자동 실행 실패: "+e.getClass().getSimpleName());
        }
    }
}
