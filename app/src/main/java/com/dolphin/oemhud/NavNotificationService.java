package com.dolphin.oemhud;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.*;

public final class NavNotificationService extends NotificationListenerService {
    @Override public void onListenerConnected(){BridgeLog.event(this,"알림 접근 연결 완료");}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!getSharedPreferences(BridgeLog.STORE,0).getBoolean("enabled",false))return;
        try{
            String pkg=sbn.getPackageName();if(!BridgeLog.isNavPackage(this,pkg))return;
            Notification n=sbn.getNotification();if(n==null||n.extras==null)return;
            Bundle b=n.extras;
            String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
            CharSequence lines=b.getCharSequence(Notification.EXTRA_BIG_TEXT,"");
            if(lines==null||lines.length()==0)lines=b.getCharSequence(Notification.EXTRA_TEXT,"");
            String text=(title+" "+lines).trim();
            NavCue cue=NavCue.fromText("알림:"+pkg,text);
            if(cue!=null)BridgeLog.cue(this,cue);
        }catch(Exception e){BridgeLog.event(this,"알림 추출 오류:"+e.getClass().getSimpleName());}
    }
}
