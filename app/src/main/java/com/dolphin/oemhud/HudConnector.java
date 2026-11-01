package com.dolphin.oemhud;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** HUD Q BLE GATT transport. One owner, latest-turn queue, bounded retry, reconnect. */
public final class HudConnector {
    public static final UUID WRITE_UUID=UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_UUID=UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String,BluetoothDevice> seen=new ConcurrentHashMap<>();
    private volatile String status="연결 안 됨";
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writer;
    private BluetoothDevice desired;
    private Listener listener;
    private boolean autoReconnect=true;
    private boolean pendingWrite;
    private boolean readyForWrite;
    private boolean descriptorPending;
    private String phase="idle";
    private long lastWriteAcceptedAt;
    private byte[] queued, inFlight;
    private final ArrayDeque<byte[]> settingFrames=new ArrayDeque<>();
    private boolean inFlightSetting;
    private boolean settingReplaySuppressed;
    private int settingErrorCount;
    private long queuedAt;
    private int writeAttempt, retryCount;
    // Timeouts require a fresh GATT session: late callbacks on the old session
    // must never acknowledge a new frame. Count these across reconnects.
    private int ackTimeoutAttempts;
    private long operationId;
    private Runnable reconnectTask;
    public interface Listener {void changed(String text);}
    public interface ScanListener {void found(ArrayList<BluetoothDevice> devices);void status(String message);}
    public HudConnector(Context ctx){context=ctx.getApplicationContext();}
    private void update(String value){
        status=value;BridgeLog.event(context,"HUD "+value);
        context.getSharedPreferences(BridgeLog.STORE,0).edit().putString("hud_status",value).apply();
        if(listener!=null)main.post(()->{Listener current=listener;if(current!=null)current.changed(value);});
    }
    public String status(){return status;}
    public synchronized boolean isReady(){return readyForWrite&&gatt!=null&&writer!=null;}
    public static boolean isSupportedName(String name){
        if(name==null)return false;
        String n=name.toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+"," ").trim();
        return n.contains("HUD Q") || n.contains("HUDQ") || n.startsWith("TPLAY HUD") || n.startsWith("ARIA HUD") || n.startsWith("MCON");
    }
    public ArrayList<BluetoothDevice> devices(){
        ArrayList<BluetoothDevice> result=new ArrayList<>(seen.values());
        result.sort((a,b)->String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
        return result;
    }
    public void scan(ScanListener l){
        try{
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
            if(a==null||!a.isEnabled()){l.status("블루투스를 켜세요");return;}
            for(BluetoothDevice d:a.getBondedDevices()) if(isSupportedName(d.getName()))seen.put(d.getAddress(),d);
            l.found(devices());
            scanner=a.getBluetoothLeScanner();
            if(scanner==null){l.status("BLE 스캐너 없음: 페어링 목록 표시");return;}
            if(callback!=null)try{scanner.stopScan(callback);}catch(Exception ignored){}
            callback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult r){
                    BluetoothDevice d=r.getDevice();
                    if(d==null||!isSupportedName(d.getName()))return;
                    seen.put(d.getAddress(),d);l.found(devices());
                }
                @Override public void onScanFailed(int code){l.status("BLE 스캔 실패 "+code);}
            };
            scanner.startScan(callback);l.status("HUD Q 검색 중 (10초)");
            ScanCallback thisScan=callback;
            main.postDelayed(()->{
                if(callback==thisScan){try{scanner.stopScan(thisScan);}catch(Exception ignored){}callback=null;}
                l.status("검색 완료: "+seen.size()+"개");
            },10000);
        }catch(Exception e){l.status("검색 실패: "+e.getClass().getSimpleName());}
    }
    private synchronized void closeGatt(){
        readyForWrite=false;writer=null;pendingWrite=false;descriptorPending=false;inFlight=null;phase="disconnected";
        BluetoothGatt old=gatt;gatt=null;
        inFlightSetting=false;settingFrames.clear();
        if(old!=null){try{old.disconnect();}catch(Exception ignored){}try{old.close();}catch(Exception ignored){}}
    }
    /** UI's scan owner must not modify the service's live BLE connection status. */
    public synchronized void stopScanning(){
        if(scanner!=null&&callback!=null)try{scanner.stopScan(callback);}catch(Exception ignored){}
        callback=null;scanner=null;
    }
    public synchronized void disconnect(){
        autoReconnect=false;desired=null;operationId++;
        if(reconnectTask!=null)main.removeCallbacks(reconnectTask);
        reconnectTask=null;queued=null;queuedAt=0;settingFrames.clear();
        stopScanning();closeGatt();update("수동 연결 해제 · 자동 재연결 중지");
    }
    public synchronized void connect(BluetoothDevice d,Listener l){
        listener=l;
        if(d==null){update("선택한 HUD 없음");return;}
        String name;
        try{name=d.getName();}catch(Exception e){update("블루투스 권한 필요");return;}
        // A known previously selected MAC is still usable after a reboot, even if Android
        // temporarily hides the BLE advertising name. Unknown devices are not accepted.
        String saved=context.getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac","");
        if(!isSupportedName(name)&&!saved.equalsIgnoreCase(d.getAddress())){
            update("HUD Q 대상이 아님: "+name);return;
        }
        desired=d;autoReconnect=true;retryCount=0;ackTimeoutAttempts=0;settingErrorCount=0;settingReplaySuppressed=false;
        if(reconnectTask!=null)main.removeCallbacks(reconnectTask);
        open();
    }
    public synchronized void retryWhenBluetoothEnabled(){
        if(autoReconnect&&desired!=null && !isReady())scheduleReconnect("블루투스 켜짐",500);
    }
    private synchronized void open(){
        if(!autoReconnect||desired==null)return;
        BluetoothAdapter adapter;
        try{
            adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null||!adapter.isEnabled()){scheduleReconnect("블루투스 OFF",3000);return;}
        }catch(SecurityException denied){
            update("블루투스 권한 없음 · 권한 확인 필요");
            return;
        }
        closeGatt();
        final long id=++operationId;
        phase="connecting";
        update("연결 요청: "+desired.getAddress());
        try{
            gatt=desired.connectGatt(context,false,new BluetoothGattCallback(){
                @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){
                    synchronized(HudConnector.this){
                        if(id!=operationId||g!=gatt)return;
                        if(code==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){
                            phase="discovering";
                            update("GATT 연결 · 서비스 검색");
                            if(!g.discoverServices())scheduleReconnect("서비스 검색 거부",1200);
                            else main.postDelayed(()->{
                                synchronized(HudConnector.this){
                                    if(id==operationId && g==gatt && !readyForWrite)
                                        scheduleReconnect("GATT 서비스/특성 응답 시간 초과",1200);
                                }
                            },11000);
                        }else{
                            closeGatt();scheduleReconnect("연결 끊김 ("+code+"/"+state+")",0);
                        }
                    }
                }
                @Override public void onServicesDiscovered(BluetoothGatt g,int code){
                    synchronized(HudConnector.this){
                        if(id!=operationId||g!=gatt)return;
                        if(code!=BluetoothGatt.GATT_SUCCESS){scheduleReconnect("서비스 검색 실패 " +code,1000);return;}
                        BluetoothGattCharacteristic found=null,notify=null;
                        for(BluetoothGattService service:g.getServices()){
                            for(BluetoothGattCharacteristic c:service.getCharacteristics()){
                                if(WRITE_UUID.equals(c.getUuid())&&(c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))!=0)found=c;
                                if(NOTIFY_UUID.equals(c.getUuid())&&(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)notify=c;
                            }
                        }
                        if(found==null){update("FFF2 쓰기 특성 없음 · 다른 모델/펌웨어");closeGatt();return;}
                        writer=found;
                        retryCount=0;
                        // CCCD is optional; enabling notifications is a separate GATT write.
                        if(notify!=null){
                            BluetoothGattDescriptor cccd=notify.getDescriptor(CCCD_UUID);
                            try{
                                if(cccd!=null && g.setCharacteristicNotification(notify,true)){
                                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    // GATT permits one ATT transaction at a time. Never write
                                    // FFF2 until the CCCD transaction completes or the GATT is
                                    // closed. A timeout must not be treated as success.
                                    descriptorPending=true;
                                    if(g.writeDescriptor(cccd)){
                                        if(descriptorPending){
                                            phase="enabling_notifications";
                                            update("FFF1 알림 설정 중");
                                        }
                                        main.postDelayed(()->{synchronized(HudConnector.this){
                                            if(id==operationId && g==gatt && descriptorPending)
                                                scheduleReconnect("FFF1 알림 설정 응답 시간 초과",1300);
                                        }},5000);
                                        return;
                                    }
                                    descriptorPending=false;
                                }
                            }catch(Exception e){BridgeLog.event(context,"CCCD 설정 불가: "+e.getClass().getSimpleName());}
                        }
                        ready();
                    }
                }
                @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int result){
                    synchronized(HudConnector.this){if(id!=operationId||g!=gatt)return;
                        if(!descriptorPending)return;
                        descriptorPending=false;
                        BridgeLog.event(context,"HUD CCCD 응답="+result);
                        if(result==BluetoothGatt.GATT_SUCCESS)ready();
                        else scheduleReconnect("FFF1 알림 설정 거부 "+result,1300);
                    }
                }
                @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int result){
                    synchronized(HudConnector.this){
                        if(id!=operationId||g!=gatt||c!=writer||!pendingWrite)return;
                        if(result==BluetoothGatt.GATT_SUCCESS){ackWrite();}
                        else failWrite("GATT 쓰기 오류 "+result);
                    }
                }
            },BluetoothDevice.TRANSPORT_LE);
            if(gatt==null)scheduleReconnect("connectGatt 결과 없음",1200);
            else main.postDelayed(()->{
                synchronized(HudConnector.this){
                    if(id==operationId && gatt!=null && "connecting".equals(phase))
                        scheduleReconnect("GATT 연결 응답 시간 초과",1200);
                }
            },13000);
        }catch(Exception e){scheduleReconnect("연결 예외 "+e.getClass().getSimpleName(),1200);}
    }
    private synchronized void ready(){
        if(gatt==null||writer==null||descriptorPending)return;
        readyForWrite=true;phase="ready";
        update("HUD Q GATT 준비 (FFF2)");
        enqueueSavedSettings();flush();
    }
    private synchronized void scheduleReconnect(String why,long minDelay){
        if(!autoReconnect||desired==null)return;
        // If GATT dropped before its write callback, retain the latest short-lived
        // navigation cue; do not replay an older turn after a long outage.
        if(inFlight!=null && !inFlightSetting && queued==null && android.os.SystemClock.elapsedRealtime()-lastWriteAcceptedAt<10000){
            queued=inFlight;queuedAt=lastWriteAcceptedAt;
        }
        closeGatt();
        if(reconnectTask!=null)main.removeCallbacks(reconnectTask);
        long delay=Math.max(minDelay,Math.min(30000,1000L << Math.min(retryCount,5)));
        retryCount=Math.min(retryCount+1,10);
        update(why+" · "+delay/1000+"초 후 재연결");
        reconnectTask=()->{synchronized(HudConnector.this){reconnectTask=null;open();}};
        main.postDelayed(reconnectTask,delay);
    }
    /** Queue one of the two verified MCon K configuration frame types. */
    public synchronized boolean sendSetting(byte[] frame){
        if(!HudSettingsProtocol.isSettingsFrame(frame)){
            BridgeLog.event(context,"잘못된 HUD 설정 명령 차단");return false;
        }
        settingReplaySuppressed=false;settingErrorCount=0;
        // Keep only the newest command of each type until the next ATT transaction.
        settingFrames.removeIf(x -> x[2]==frame[2]);
        settingFrames.addLast(frame.clone());
        BridgeLog.event(context,"HUD 설정 명령 큐: "+MconHudQProtocol.hex(frame));
        if(!isReady())return false;
        flush();return true;
    }
    private synchronized void enqueueSavedSettings(){
        if(settingReplaySuppressed)return;
        android.content.SharedPreferences p=context.getSharedPreferences(BridgeLog.STORE,0);
        // Avoid replaying a previous session's stale settings queue.
        settingFrames.clear();
        if(p.getBoolean("hud_buzzer_saved",false))
            settingFrames.addLast(HudSettingsProtocol.buzzer(p.getBoolean("hud_buzzer_enabled",true),p.getInt("hud_buzzer_level",1)));
        if(p.getBoolean("hud_brightness_saved",false)) {
            int day=p.getInt("hud_brightness_day",2);
            int night=p.getBoolean("hud_brightness_auto",true)?p.getInt("hud_brightness_night",0):day;
            settingFrames.addLast(HudSettingsProtocol.brightness(day,night));
        }
    }
    public synchronized boolean sendTurn(NavCue cue){
        byte[] frame=MconHudQProtocol.fromCue(cue);
        if(frame==null){BridgeLog.event(context,"TBT 미지원 방향/거리 → 송신 안 함");return false;}
        queued=frame;queuedAt=android.os.SystemClock.elapsedRealtime();ackTimeoutAttempts=0;
        if(!isReady()){BridgeLog.event(context,"HUD 연결 대기 · 최신 안내 10초 보관");return false;}
        flush();return true;
    }
    private synchronized void flush(){
        if(!readyForWrite||gatt==null||writer==null||pendingWrite)return;
        if(!settingFrames.isEmpty()){
            inFlight=settingFrames.removeFirst();inFlightSetting=true;writeAttempt=0;
            transmit();return;
        }
        if(queued==null)return;
        if(android.os.SystemClock.elapsedRealtime()-queuedAt>10000){queued=null;return;}
        inFlight=queued;queued=null;inFlightSetting=false;writeAttempt=0;
        transmit();
    }
    private synchronized void transmit(){
        if(gatt==null||writer==null||inFlight==null)return;
        byte[] bytes=inFlight;
        try{
            int props=writer.getProperties();
            boolean acknowledged=(props&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0;
            writer.setWriteType(acknowledged?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            writer.setValue(bytes);
            pendingWrite=true;
            final long id=operationId;
            final int attempt=++writeAttempt;
            final boolean wasSetting=inFlightSetting;
            if(!gatt.writeCharacteristic(writer)){failWrite("writeCharacteristic 거부");return;}
            String hex=MconHudQProtocol.hex(bytes);
            lastWriteAcceptedAt=android.os.SystemClock.elapsedRealtime();
            BridgeLog.event(context,"HUD TX 요청 #"+attempt+" "+hex);
            context.getSharedPreferences(BridgeLog.STORE,0).edit().putString("last_tx",hex).apply();
            main.postDelayed(()->{
                synchronized(HudConnector.this){
                    if(id!=operationId||!pendingWrite||attempt!=writeAttempt)return;
                    if(!acknowledged){ackWrite();}
                    else{
                        ackTimeoutAttempts++;
                        android.content.SharedPreferences ps=context.getSharedPreferences(BridgeLog.STORE,0);
                        ps.edit().putInt("hud_tx_errors",ps.getInt("hud_tx_errors",0)+1).apply();
                        BridgeLog.event(context,"GATT 응답 무응답: 안전을 위해 연결 폐기 #"+ackTimeoutAttempts);
                        if(ackTimeoutAttempts>=3){
                            if(wasSetting){
                                settingReplaySuppressed=true;
                                settingFrames.clear();
                                BridgeLog.event(context,"HUD 설정 미응답 3회 · 자동 재적용 일시 중단");
                            }
                            // Avoid endless duplicate arrows when the HUD never responds.
                            inFlight=null;pendingWrite=false;queued=null;
                            closeGatt();update("HUD 무응답 3회 · 자동 재전송 중단");
                            scheduleReconnect("다음 안내를 위한 BLE 재연결",2000);
                        }else scheduleReconnect("GATT ACK 없음 → 새 연결에서 재전송",1000);
                    }
                }
            },acknowledged?2500:180);
        }catch(Exception e){failWrite("전송 오류 "+e.getClass().getSimpleName());}
    }
    private synchronized void ackWrite(){
        boolean wasSetting=inFlightSetting;
        if(wasSetting){
            settingErrorCount=0;
            context.getSharedPreferences(BridgeLog.STORE,0).edit()
              .putString("hud_setting_status","BLE 설정 쓰기 완료 · HUD 적용은 미확인").apply();
        }
        pendingWrite=false;inFlight=null;inFlightSetting=false;ackTimeoutAttempts=0;
        android.content.SharedPreferences preferences=context.getSharedPreferences(BridgeLog.STORE,0);
        preferences.edit().putInt("hud_tx_completed",preferences.getInt("hud_tx_completed",0)+1).apply();
        BridgeLog.event(context,wasSetting?"HUD 설정 GATT 쓰기 완료(기기 반영 미확인)":"HUD GATT 쓰기 완료 (화면 표시 성공을 뜻하지 않음)");
        main.postDelayed(this::flush,70);
    }
    private synchronized void failWrite(String error){
        pendingWrite=false;
        android.content.SharedPreferences preferences=context.getSharedPreferences(BridgeLog.STORE,0);
        preferences.edit().putInt("hud_tx_errors",preferences.getInt("hud_tx_errors",0)+1).apply();
        BridgeLog.event(context,error);
        if(inFlight!=null&&writeAttempt<3&&gatt!=null&&writer!=null){
            final int attempt=writeAttempt;
            final long generation=operationId;
            final byte[] failedFrame=inFlight;
            main.postDelayed(()->{synchronized(HudConnector.this){
                if(generation==operationId&&!pendingWrite&&inFlight==failedFrame&&attempt==writeAttempt)transmit();
            }},400L*attempt);
        }else{
            if(inFlightSetting){
                settingErrorCount++;
                context.getSharedPreferences(BridgeLog.STORE,0).edit().putString("hud_setting_status","설정 쓰기 실패").apply();
                if(settingErrorCount>=3){settingReplaySuppressed=true;settingFrames.clear();}
            }
            inFlight=null;inFlightSetting=false;
            update("HUD 쓰기 실패(최대 재시도 완료)");main.postDelayed(this::flush,100);
        }
    }
}
