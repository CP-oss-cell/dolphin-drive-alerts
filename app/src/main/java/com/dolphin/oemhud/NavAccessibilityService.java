package com.dolphin.oemhud;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Reads only navigation-app event and visible accessibility text after explicit user permission. */
public final class NavAccessibilityService extends AccessibilityService {
    private String lastText=""; private long lastAt;
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!getSharedPreferences(BridgeLog.STORE,0).getBoolean("enabled",false))return;
        if(event==null||event.getPackageName()==null)return;
        String pkg=String.valueOf(event.getPackageName());
        if(!BridgeLog.isNavPackage(this,pkg))return;
        StringBuilder sb=new StringBuilder();
        List<CharSequence> parts=event.getText();
        if(parts!=null)for(CharSequence x:parts){if(x!=null&&sb.length()<250)sb.append(x).append(' ');}
        NavCue cue=NavCue.fromText("화면:"+pkg,sb.toString());
        // Access the visible UI tree only if the lightweight event text could not reveal a cue.
        if(cue==null){
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root!=null && root.getPackageName()!=null && pkg.equals(String.valueOf(root.getPackageName()))){
                StringBuilder visible=new StringBuilder();
                collect(root,visible,0,new int[]{0});
                cue=NavCue.fromText("화면트리:"+pkg,visible.toString());
            }
        }
        if(cue==null||!cue.isUsable())return;
        String key=cue.direction+"/"+cue.distanceMeters;
        long now=System.currentTimeMillis();
        if(key.equals(lastText)&&now-lastAt<1400)return;
        lastText=key;lastAt=now;BridgeLog.cue(this,cue);
    }
    private static void collect(AccessibilityNodeInfo node,StringBuilder out,int depth,int[] visited){
        if(node==null||depth>7||visited[0]++>60||out.length()>350)return;
        CharSequence text=node.getText();if(text!=null)out.append(text).append(' ');
        CharSequence description=node.getContentDescription();if(description!=null)out.append(description).append(' ');
        for(int i=0;i<node.getChildCount()&&i<20&&out.length()<350;i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null){try{collect(child,out,depth+1,visited);}finally{child.recycle();}}
        }
    }
    @Override public void onInterrupt(){}
}
