package com.dolphin.oemhud;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.dolphin.oemhud.adb.HudAdbPermissions;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/**
 * Simple, single-screen Korean dashboard. Each setting has exactly one owner:
 * connection, operation, HUD display/sound, permissions, advanced diagnostics.
 * This UI does not change OEM vehicle signals or promise hardware confirmation.
 */
public final class MainActivity extends Activity {
    private static final int BT_REQ = 305;
    private static final int NAVY=Color.rgb(20,43,68), INK=Color.rgb(30,47,67);
    private static final int MUTED=Color.rgb(101,119,139), TEAL=Color.rgb(0,129,135);
    private static final int SURFACE=Color.rgb(244,247,251), WHITE=Color.WHITE;
    private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refreshTask=new Runnable(){@Override public void run(){updateStatus();main.postDelayed(this,3000);}};
    private HudConnector scanner;
    private SharedPreferences prefs;
    private TextView hudState, navState, rightsState, details, operationDescription;
    private Spinner hudPicker;
    private Switch buzzerEnabled;
    private Spinner volume, brightnessMode, brightnessDay, brightnessNight;
    private EditText packageInput;
    private Button operationButton, adbButton;
    private LinearLayout advancedContent;
    private boolean advancedVisible;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences(BridgeLog.STORE,0);
        scanner=new HudConnector(this);
        buildDashboard();
        if(!hasBluetoothPermission())requestBluetoothPermission();
        if(prefs.getBoolean("enabled",false)){
            try{startServiceSafely(new Intent(this,BridgeService.class));}
            catch(Exception e){BridgeLog.event(this,"자동 시작 실패: "+e.getClass().getSimpleName());}
        }
        if(!prefs.getBoolean("adb_first_run_prompt",false)){
            prefs.edit().putBoolean("adb_first_run_prompt",true).apply();
            new AlertDialog.Builder(this).setTitle("차량 ADB 권한 설정")
                .setMessage("순정 내비 안내 수신을 위해 차량 로컬 ADB 승인이 필요할 수 있습니다. 차량이 ADB를 제공하는 경우에만 승인창이 나타납니다. 지금 진행할까요?")
                .setNegativeButton("나중에",null)
                .setPositiveButton("승인 및 권한 설정",(d,w)->requestAdbSetup()).show();
        }
    }
    private void buildDashboard(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=column();root.setPadding(dp(16),dp(18),dp(16),dp(28));root.setBackgroundColor(SURFACE);
        scroll.addView(root);

        LinearLayout hero=column();hero.setPadding(dp(20),dp(19),dp(20),dp(18));
        hero.setBackground(rounded(NAVY,20));
        TextView small=label("BYD DOLPHIN  ·  HUD Q",12,Color.rgb(127,219,220),true);hero.addView(small);
        TextView title=label("순정 내비 HUD 대시보드",23,WHITE,true); title.setPadding(0,dp(7),0,dp(5));hero.addView(title);
        hero.addView(label("MCON K 없이 길안내 중계 · v0.9",13,Color.rgb(217,231,240),false));
        root.addView(hero,margin(0,0,0,13));

        LinearLayout statusRow=new LinearLayout(this);statusRow.setOrientation(LinearLayout.HORIZONTAL);
        hudState=smallStatus(statusRow,"HUD 연결");
        navState=smallStatus(statusRow,"순정 TBT");
        rightsState=smallStatus(statusRow,"권한");
        root.addView(statusRow,margin(0,0,0,13));

        LinearLayout operation=card(root,"운행 연동","시동 후 백그라운드 자동 연결·안내 중계",false);
        operationDescription=label("현재 상태를 확인하는 중입니다.",13,MUTED,false);operation.addView(operationDescription,margin(0,0,0,8));
        operationButton=button(operation,"연동 시작",true,()->toggleRunning());
        operation.addView(label("순정 내비와 HUD를 대신 실행하지 않습니다. 차량 내비는 평소대로 사용하세요.",12,MUTED,false),margin(0,8,0,0));

        LinearLayout connection=card(root,"HUD 연결","HUD Q 선택 · 자동 연결 · 연결 해제",false);
        LinearLayout searchRow=new LinearLayout(this);searchRow.setOrientation(LinearLayout.HORIZONTAL);
        addWeightedButton(searchRow,"HUD 검색",false,()->loadPaired());
        addWeightedButton(searchRow,"블루투스 설정",false,()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        connection.addView(searchRow);
        hudPicker=spinner(connection,new String[]{"HUD 검색을 눌러 기기를 선택하세요"},0);
        button(connection,"선택한 HUD 연결·자동 연결 저장",true,()->connectSelected());
        button(connection,"HUD 연결 해제",false,()->{
            Intent svc=new Intent(this,BridgeService.class).setAction(BridgeService.ACTION_DISCONNECT);
            startServiceSafely(svc);toast("HUD 자동 연결 해제");updateStatus();
        });

        LinearLayout settings=card(root,"경보음·밝기","하나의 설정 화면에서 한 번에 저장",false);
        buzzerEnabled=new Switch(this);buzzerEnabled.setText("HUD 경보음 사용");
        buzzerEnabled.setTextColor(INK);buzzerEnabled.setChecked(prefs.getBoolean("hud_buzzer_enabled",true));
        settings.addView(buzzerEnabled,margin(0,0,0,5));
        settings.addView(label("경보음 크기",13,INK,true),margin(0,7,0,2));
        volume=spinner(settings,new String[]{"작게","보통","크게"},prefs.getInt("hud_buzzer_level",1));
        settings.addView(label("밝기 모드",13,INK,true),margin(0,10,0,2));
        brightnessMode=spinner(settings,new String[]{"자동 · HUD 자체 주야간 전환","고정 밝기"},prefs.getBoolean("hud_brightness_auto",true)?0:1);
        settings.addView(label("주간 / 고정 밝기",13,INK,true),margin(0,10,0,2));
        brightnessDay=spinner(settings,levels(),prefs.getInt("hud_brightness_day",2));
        settings.addView(label("야간 밝기",13,INK,true),margin(0,10,0,2));
        brightnessNight=spinner(settings,levels(),prefs.getInt("hud_brightness_night",0));
        brightnessMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){
                brightnessNight.setEnabled(position==0);
            }
        });
        button(settings,"경보음·밝기 함께 저장 및 전송",true,this::saveHudSettings);
        settings.addView(label("자동 밝기는 HUD 자체 주야간 기능이 있는 경우에만 적용됩니다. GATT 전송 완료는 실제 밝기·음량 변경의 확인이 아닙니다.",12,MUTED,false),margin(0,8,0,0));

        LinearLayout permission=card(root,"권한 관리","ADB 승인 및 필수 권한을 한 번에 확인",false);
        adbButton=button(permission,"ADB 연결·권한 적용",true,this::requestAdbSetup);
        permission.addView(label("ADB 인증, 알림 접근, 위치 권한 순으로 처리합니다. 접근성 접근은 별도 동의 후 선택적으로 사용합니다.",12,MUTED,false),margin(0,8,0,0));

        LinearLayout advanced=card(root,"고급 설정 · 진단","일반 사용 중에는 열 필요 없음",false);
        advancedContent=column();advancedContent.setVisibility(View.GONE);
        button(advanced,"고급 설정 및 진단 보기",false,()->{
            advancedVisible=!advancedVisible;
            advancedContent.setVisibility(advancedVisible?View.VISIBLE:View.GONE);
            if(advancedVisible)updateStatus();
        });
        advanced.addView(advancedContent);
        advancedContent.addView(label("순정 내비 패키지 지정 · 비워두면 자동 감지",13,INK,true),margin(0,12,0,2));
        packageInput=new EditText(this);packageInput.setSingleLine(true);packageInput.setTextSize(14);
        packageInput.setHint("내비 패키지명 (선택)");packageInput.setText(prefs.getString("nav_package",""));
        advancedContent.addView(packageInput);
        button(advancedContent,"내비 앱 지정 저장",false,()->{
            prefs.edit().putString("nav_package",packageInput.getText().toString().trim()).apply();toast("내비 앱 설정 저장");
        });
        button(advancedContent,"접근성 권한 적용 (선택)",false,()->{
            new AlertDialog.Builder(this).setTitle("화면 안내 읽기 권한")
                .setMessage("기존 접근성 서비스 목록을 보존한 채 순정 내비 화면에서 안내 텍스트를 읽는 기능을 추가합니다. 직접 동의하는 경우에만 실행합니다.")
                .setNegativeButton("취소",null).setPositiveButton("적용",(d,w)->{
                    HudAdbPermissions.apply(this,true,(msg,done)->runOnUiThread(()->{
                        BridgeLog.event(this,"접근성: "+msg);refreshDetails(msg);updateStatus();
                    }));
                }).show();
        });
        button(advancedContent,"알림 접근 권한 설정 열기",false,()->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        button(advancedContent,"접근성 설정 열기",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(advancedContent,"권한 상태 다시 확인",false,()->{
            HudAdbPermissions.audit(this,(msg,done)->runOnUiThread(()->{refreshDetails(msg);updateStatus();}));
        });
        details=label("",12,INK,false);details.setTextIsSelectable(true);details.setPadding(0,dp(12),0,dp(8));
        advancedContent.addView(details);
        button(advancedContent,"진단 ZIP 저장·공유",true,this::exportZip);
        advancedContent.addView(label("카메라 단속 정보 전달, 실물 HUD 표시, 아리아 음성인식은 확인되거나 구현된 기능으로 표시하지 않습니다.",12,MUTED,false),margin(0,8,0,0));

        root.addView(label("차량 ECU·계기판 제어 없음 · 경고/방향 안내는 순정 내비 화면을 우선하세요.",12,MUTED,false),margin(2,4,2,0));
        setContentView(scroll);updateStatus();
    }
    private void toggleRunning(){
        boolean enabled=!prefs.getBoolean("enabled",false);
        prefs.edit().putBoolean("enabled",enabled).apply();
        try{if(enabled)startServiceSafely(new Intent(this,BridgeService.class));
            else stopService(new Intent(this,BridgeService.class));
            toast(enabled?"HUD 길안내 연동 시작":"HUD 길안내 연동 중지");
        }catch(Exception e){prefs.edit().putBoolean("enabled",!enabled).apply();toast("작동 변경 실패: "+e.getClass().getSimpleName());}
        updateStatus();
    }
    private void connectSelected(){
        int pos=hudPicker.getSelectedItemPosition();
        if(pos<0||pos>=devices.size()){toast("먼저 HUD를 검색하고 선택하세요");return;}
        if(!hasBluetoothPermission()){requestBluetoothPermission();return;}
        try{
            BluetoothDevice d=devices.get(pos);
            if(!HudConnector.isSupportedName(d.getName())){toast("지원 대상 HUD Q가 아닙니다");return;}
            String addr=d.getAddress();prefs.edit().putString("hud_mac",addr).apply();
            Intent intent=new Intent(this,BridgeService.class).setAction(BridgeService.ACTION_CONNECT);
            intent.putExtra(BridgeService.EXTRA_ADDRESS,addr);startServiceSafely(intent);
            scanner.stopScanning();toast("HUD 연결·자동 연결 저장");updateStatus();
        }catch(Exception ex){toast("HUD 연결 요청 실패: "+ex.getClass().getSimpleName());}
    }
    private void saveHudSettings(){
        // Saved by BridgeService: original wire format and reconnection replay preserved.
        Intent buzzer=new Intent(this,BridgeService.class).setAction(BridgeService.ACTION_BUZZER);
        buzzer.putExtra("enabled",buzzerEnabled.isChecked());buzzer.putExtra("level",volume.getSelectedItemPosition());
        Intent bright=new Intent(this,BridgeService.class).setAction(BridgeService.ACTION_BRIGHTNESS);
        bright.putExtra("auto",brightnessMode.getSelectedItemPosition()==0);
        bright.putExtra("day",brightnessDay.getSelectedItemPosition());
        bright.putExtra("night",brightnessNight.getSelectedItemPosition());
        try{startServiceSafely(buzzer);startServiceSafely(bright);
            toast("HUD 경보음·밝기 저장 및 전송 요청");updateStatus();
        }catch(Exception e){toast("설정 전송 오류: "+e.getClass().getSimpleName());}
    }
    private void requestAdbSetup(){
        new AlertDialog.Builder(this).setTitle("차량 ADB 연결 및 권한 적용")
            .setMessage("차량 로컬 ADB(127.0.0.1:5555) 승인을 요청하고, 승인되면 이 앱의 알림·블루투스 검색 권한을 적용합니다. 차량 ECU를 변경하지 않습니다. 진행할까요?")
            .setNegativeButton("취소",null).setPositiveButton("연결·적용",(d,w)->startAdbSetup()).show();
    }
    private void startAdbSetup(){
        adbButton.setEnabled(false);adbButton.setText("ADB 연결 중...");
        HudAdbPermissions.connect(this,(msg,done)->runOnUiThread(()->{
            prefs.edit().putString("adb_last_result",msg).apply();updateStatus();
            if(!done)return;
            if(!msg.startsWith("ADB_READY:")){
                adbButton.setEnabled(true);adbButton.setText("ADB 연결·권한 적용");
                new AlertDialog.Builder(this).setTitle("ADB 연결 실패").setMessage(msg).setPositiveButton("확인",null).show();
                return;
            }
            adbButton.setText("필수 권한 적용 중...");
            HudAdbPermissions.apply(this,false,(result,finished)->runOnUiThread(()->{
                prefs.edit().putString("adb_last_result",result).apply();updateStatus();
                if(finished){
                    adbButton.setEnabled(true);adbButton.setText("ADB 연결·권한 적용");
                    new AlertDialog.Builder(this).setTitle("ADB 권한 적용 결과").setMessage(result)
                        .setPositiveButton("확인",null).show();
                }
            }));
        }));
    }
    private void loadPaired(){
        if(!hasBluetoothPermission()){requestBluetoothPermission();return;}
        scanner.scan(new HudConnector.ScanListener(){
            @Override public void found(ArrayList<BluetoothDevice> found){runOnUiThread(()->{
                devices.clear();devices.addAll(found);ArrayList<String> names=new ArrayList<>();
                for(BluetoothDevice d:found){
                    try{String a=d.getAddress(),n=d.getName();
                        names.add((n==null?"HUD Q":n)+" ("+a.substring(0,Math.min(5,a.length()))+"…)");}
                    catch(Exception ex){names.add("HUD Q 기기");}
                }
                if(names.isEmpty())names.add("검색된 HUD 없음");
                ArrayAdapter<String> adapter=new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_item,names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);hudPicker.setAdapter(adapter);
            });}
            @Override public void status(String message){runOnUiThread(()->{
                BridgeLog.event(MainActivity.this,message);
                if(!message.startsWith("검색 중"))toast(message);
            });}
        });
    }
    private boolean hasBluetoothPermission(){
        return (Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED))
            && (Build.VERSION.SDK_INT>=31 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
    }
    private void requestBluetoothPermission(){
        if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},BT_REQ);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},BT_REQ);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==BT_REQ){if(hasBluetoothPermission())loadPaired();else toast("HUD 검색을 위해 블루투스·위치 권한을 허용하세요");}
        updateStatus();
    }
    private void startServiceSafely(Intent i){
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }
    private void updateStatus(){
        if(prefs==null||operationButton==null)return;
        boolean running=prefs.getBoolean("enabled",false);
        operationButton.setText(running?"연동 중지":"연동 시작 · 자동 실행");
        operationDescription.setText(running?"안내 수신 및 HUD 연결 유지 사용 중":"연동 꺼짐 · 누르면 자동 실행 설정도 함께 켜집니다");
        String hud=prefs.getString("hud_status","연결 전");
        hudState.setText(hud.contains("준비")?"BLE 준비":hud.length()>25?hud.substring(0,24)+"…":hud);
        int count=prefs.getInt("oem_tbt_count",0);navState.setText(count==0?"수신 전":count+"회 수신");
        String adb=prefs.getString("adb_last_result","");
        rightsState.setText(adb.startsWith("ADB_READY")?"최근 승인":adb.isEmpty()?"설정 확인":"상태 확인");
        if(advancedVisible)refreshDetails("");
    }
    private String statusText(){
        return HudAdbPermissions.readStatus(this)+"\n\n"+
            "브리지 자동 실행: "+(prefs.getBoolean("enabled",false)?"ON":"OFF")+"\n"+
            "HUD: "+prefs.getString("hud_status","연결 전")+"\n"+
            "경보음: "+(prefs.getBoolean("hud_buzzer_saved",false)?(prefs.getBoolean("hud_buzzer_enabled",true)?"ON":"OFF")+" / "+(prefs.getInt("hud_buzzer_level",1)+1)+"단계":"미설정")+"\n"+
            "밝기: "+(prefs.getBoolean("hud_brightness_saved",false)?(prefs.getBoolean("hud_brightness_auto",true)?"주야간 프로필":"고정")+" / 주간 "+(prefs.getInt("hud_brightness_day",2)+1)+" / 야간 "+(prefs.getInt("hud_brightness_night",0)+1):"미설정")+"\n"+
            "설정 송신: "+prefs.getString("hud_setting_status","없음")+"\n"+
            "순정 TBT 방송: "+prefs.getInt("oem_tbt_count",0)+"회"+"\n"+
            "마지막 순정 방송: "+prefs.getString("last_oem_tbt","없음")+"\n"+
            "GATT 쓰기 완료: "+prefs.getInt("hud_tx_completed",0)+" / 오류: "+prefs.getInt("hud_tx_errors",0)+"\n"+
            "최근 전송: "+prefs.getString("last_tx","없음")+"\n"+
            "최근 안내: "+prefs.getString("last_cue","없음")+"\n"+
            "최근 ADB 결과: "+prefs.getString("adb_last_result","없음")+"\n"+
            "※ GATT 완료는 실물 HUD 화면 표시 성공을 증명하지 않습니다.";
    }
    private void refreshDetails(String extra){
        if(details!=null)details.setText((extra.isEmpty()?"":extra+"\n\n")+statusText());
    }
    private void exportZip(){
        Uri uri=null;boolean completed=false;
        try{
            String name="DolphinOemHud_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.KOREA).format(new Date())+".zip";
            ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);
            values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
            values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("다운로드 저장소 생성 실패");
            try(OutputStream out=getContentResolver().openOutputStream(uri);ZipOutputStream zip=new ZipOutputStream(out)){
                zip.putNextEntry(new ZipEntry("report.txt"));
                String report="Dolphin OEM HUD Bridge v0.9\n"+statusText()+"\n\n=== EVENTS ===\n"+
                    prefs.getString("history","")+"\n=== NOTE ===\nGATT write != HUD screen confirmation.\n";
                zip.write(report.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);
            getContentResolver().update(uri,ready,null,null);completed=true;
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("application/zip");
            share.putExtra(Intent.EXTRA_STREAM,uri);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"HUD 진단 ZIP 공유"));
        }catch(Exception e){toast("ZIP 저장/공유 실패: "+e.getMessage());}
        finally{if(!completed&&uri!=null)getContentResolver().delete(uri,null,null);}
    }
    @Override protected void onResume(){super.onResume();main.removeCallbacks(refreshTask);main.post(refreshTask);}
    @Override protected void onPause(){main.removeCallbacks(refreshTask);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacks(refreshTask);scanner.stopScanning();super.onDestroy();}

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout.LayoutParams margin(int left,int top,int right,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(left),dp(top),dp(right),dp(bottom));return p;
    }
    private TextView label(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private LinearLayout card(LinearLayout root,String title,String sub,boolean ignored){
        LinearLayout card=column();card.setBackground(rounded(WHITE,18));card.setPadding(dp(17),dp(17),dp(17),dp(17));
        card.setElevation(dp(2));card.addView(label(title,18,INK,true));
        card.addView(label(sub,12,MUTED,false),margin(0,3,0,12));root.addView(card,margin(0,0,0,12));return card;
    }
    private TextView smallStatus(LinearLayout parent,String heading){
        LinearLayout wrapper=column();wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(dp(10),dp(12),dp(9),dp(12));wrapper.setBackground(rounded(WHITE,14));
        wrapper.setElevation(dp(1));
        wrapper.addView(label(heading,11,MUTED,false));
        TextView result=label("확인 중",12,INK,true);result.setSingleLine(true);result.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wrapper.addView(result,margin(0,5,0,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(66),1f);
        p.setMargins(dp(2),0,dp(2),0);parent.addView(wrapper,p);return result;
    }
    private Button button(LinearLayout host,String title,boolean primary,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(13);b.setMinHeight(dp(45));
        b.setTextColor(primary?WHITE:INK);
        GradientDrawable background=rounded(primary?TEAL:Color.rgb(231,237,243),12);
        b.setBackground(background);host.addView(b,margin(0,5,0,2));
        b.setOnClickListener(v->action.run());return b;
    }
    private void addWeightedButton(LinearLayout host,String title,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);
        b.setTextColor(primary?WHITE:INK);b.setBackground(rounded(primary?TEAL:Color.rgb(231,237,243),12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1f);p.setMargins(dp(2),0,dp(2),0);
        host.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private Spinner spinner(LinearLayout host,String[] choices,int selected){
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);s.setSelection(Math.max(0,Math.min(choices.length-1,selected)));
        host.addView(s,margin(0,3,0,8));return s;
    }
    private String[] levels(){return new String[]{"1단계 · 어둡게","2단계 · 보통","3단계 · 밝게"};}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
