package com.dolphin.oemhud;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Keeps the TBT->HUD link running without MCon K. No OEM vehicle writes. */
public final class BridgeService extends Service {
    public static final String ACTION_CONNECT="com.dolphin.oemhud.CONNECT";
    public static final String ACTION_DISCONNECT="com.dolphin.oemhud.DISCONNECT";
    public static final String ACTION_DEMO="com.dolphin.oemhud.DEMO";
    public static final String ACTION_BUZZER="com.dolphin.oemhud.SET_BUZZER";
    public static final String ACTION_BRIGHTNESS="com.dolphin.oemhud.SET_BRIGHTNESS";
    public static final String EXTRA_ADDRESS="address";
    private static volatile BridgeService active;
    private static final String CH="navbridge_status";
    private BroadcastReceiver receiver;
    private BroadcastReceiver bluetoothReceiver;
    private HudConnector hud;
    private String lastCueKey="";
    private long lastCueAt;
    @Override public void onCreate(){
        super.onCreate();active=this;hud=new HudConnector(this);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CH,"내비 HUD 연결",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
        Notification n=nb.setContentTitle("돌핀 순정내비 → HUD Q").setContentText("TBT 수신 및 HUD 연결 유지")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(pending).setOngoing(true).build();
        startForeground(4109,n);
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context,Intent intent){
                if(!getSharedPreferences(BridgeLog.STORE,0).getBoolean("enabled",false))return;
                Bundle b=intent.getExtras();if(b==null)return;
                try{
                    int type=NavCue.number(b,-1,"KEY_TYPE");
                    if(type==10001){
                        android.content.SharedPreferences p=getSharedPreferences(BridgeLog.STORE,0);
                        int count=p.getInt("oem_tbt_count",0)+1;
                        NavCue cue=NavCue.fromAutonavi(b);
                        p.edit().putInt("oem_tbt_count",count)
                          .putString("last_oem_tbt",cue==null?"해석 없음":cue.display()).apply();
                        BridgeLog.event(context,"AutoNavi TBT#"+count+" "+safeExtras(b));
                        if(cue!=null)BridgeLog.cue(context,cue);
                    }else if(type==10019){BridgeLog.event(context,"AutoNavi 상태="+NavCue.number(b,-1,"EXTRA_STATE"));}
                }catch(Exception e){BridgeLog.event(context,"순정방송 해석 오류: "+e.getClass().getSimpleName());}
            }
        };
        IntentFilter f=new IntentFilter("AUTONAVI_STANDARD_BROADCAST_SEND");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);else registerReceiver(receiver,f);
        bluetoothReceiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction()) &&
                   i.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)==BluetoothAdapter.STATE_ON)
                   hud.retryWhenBluetoothEnabled();
            }
        };
        registerReceiver(bluetoothReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        BridgeLog.event(this,"HUD 브리지 서비스 가동 · OEM 방송/알림/접근성 수신 대기");
    }
    private static String safeExtras(Bundle b){
        ArrayList<String> fields=new ArrayList<>();
        String[] priority={"KEY_TYPE","NEW_ICON","ICON","SEG_REMAIN_DIS","SEG_REMAIN_DIS_AUTO","CUR_SPEED","NEXT_ROAD_NAME","CUR_ROAD_NAME"};
        for(String key:priority)if(b.containsKey(key))fields.add(key);
        ArrayList<String> other=new ArrayList<>(b.keySet());Collections.sort(other);
        for(String key:other)if(!fields.contains(key))fields.add(key);
        StringBuilder out=new StringBuilder();int count=0;
        for(String key:fields){
            String upper=key.toUpperCase(Locale.ROOT);
            if(upper.contains("GPS")||upper.contains("LAT")||upper.contains("LON")||upper.contains("ADDR")||upper.contains("DEST")||upper.contains("POI"))continue;
            Object value=b.get(key);if(value==null)continue;
            String shown=String.valueOf(value).replace('\n',' ').replace('\r',' ');
            if(shown.length()>32)shown=shown.substring(0,32);
            if(out.length()>250||count++>=16)break;
            out.append(key).append('=').append(shown).append(' ');
        }
        return out.toString().trim();
    }
    public static void onCue(NavCue cue){
        BridgeService a=active;if(a!=null)a.acceptCue(cue);
    }
    private synchronized void acceptCue(NavCue cue){
        if(cue==null||!cue.isUsable())return;
        if(!getSharedPreferences(BridgeLog.STORE,0).getBoolean("enabled",false))return;
        String key=cue.direction+"/"+cue.distanceMeters;
        long now=System.currentTimeMillis();
        if(key.equals(lastCueKey)&&now-lastCueAt<1800)return;
        lastCueKey=key;lastCueAt=now;
        boolean result=hud.sendTurn(cue);
        BridgeLog.event(this,result?"TBT→HUD 큐 접수 (GATT 완료 여부 별도 확인)":"TBT→HUD 전송 대기 (BLE 연결/패킷 확인 필요)");
    }
    private void connect(String mac){
        if(mac==null || mac.isEmpty())return;
        try{
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null||!adapter.isEnabled())return;
            BluetoothDevice d=adapter.getRemoteDevice(mac);
            hud.connect(d,s->{});
        }catch(Exception e){BridgeLog.event(this,"HUD 자동 연결 실패 "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_DISCONNECT.equals(action)){hud.disconnect();getSharedPreferences(BridgeLog.STORE,0).edit().remove("hud_mac").apply();}
        else if(ACTION_CONNECT.equals(action)){
            String mac=intent.getStringExtra(EXTRA_ADDRESS);
            if(mac!=null){getSharedPreferences(BridgeLog.STORE,0).edit().putString("hud_mac",mac).apply();connect(mac);}
        }else if(ACTION_BUZZER.equals(action)){
            boolean enabled=intent.getBooleanExtra("enabled",true);
            int level=intent.getIntExtra("level",1);
            try{
                byte[] frame=HudSettingsProtocol.buzzer(enabled,level);
                getSharedPreferences(BridgeLog.STORE,0).edit().putBoolean("hud_buzzer_saved",true)
                    .putBoolean("hud_buzzer_enabled",enabled).putInt("hud_buzzer_level",level)
                    .putString("hud_setting_status","부저 설정 전송 대기 / 전송 시도").apply();
                if(!hud.isReady())connect(getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac",""));
                else hud.sendSetting(frame);
            }catch(IllegalArgumentException e){BridgeLog.event(this,"잘못된 부저 설정 차단");}
        }else if(ACTION_BRIGHTNESS.equals(action)){
            boolean auto=intent.getBooleanExtra("auto",true);
            int day=intent.getIntExtra("day",2),night=intent.getIntExtra("night",0);
            try{
                byte[] frame=HudSettingsProtocol.brightness(day,auto?night:day);
                getSharedPreferences(BridgeLog.STORE,0).edit().putBoolean("hud_brightness_saved",true)
                    .putBoolean("hud_brightness_auto",auto).putInt("hud_brightness_day",day)
                    .putInt("hud_brightness_night",night)
                    .putString("hud_setting_status","밝기 설정 전송 대기 / 전송 시도").apply();
                if(!hud.isReady())connect(getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac",""));
                else hud.sendSetting(frame);
            }catch(IllegalArgumentException e){BridgeLog.event(this,"잘못된 밝기 설정 차단");}
        }else if(ACTION_DEMO.equals(action)){
            // Demo requires explicit user action while stationary. If the process was
            // restarted, initiate the saved HUD connection before queueing the cue.
            if(!hud.isReady())connect(getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac",""));
            NavCue demo=new NavCue("수동 시험","우회전",300,"HUD 시험",-1,-1,"");
            hud.sendTurn(demo);
        }else{
            connect(getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac",""));
        }
        return START_STICKY;
    }
    @Override public void onDestroy(){
        active=null;
        if(receiver!=null)unregisterReceiver(receiver);
        if(bluetoothReceiver!=null)unregisterReceiver(bluetoothReceiver);
        hud.disconnect();super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
