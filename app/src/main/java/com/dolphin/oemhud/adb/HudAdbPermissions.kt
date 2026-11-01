package com.dolphin.oemhud.adb

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.dolphin.oemhud.NavAccessibilityService
import com.dolphin.oemhud.NavNotificationService

/** ADB transport is local and own-key. Authorization and grants are separate explicit actions. */
object HudAdbPermissions {
    interface Callback { fun onResult(message: String, done: Boolean) }
    private val working=java.util.concurrent.atomic.AtomicBoolean(false)
    private fun notify(cb:Callback, s:String, done:Boolean=false) = cb.onResult(s,done)
    private fun shell(ctx: Context, command: String) = NativeAdbClient.executeShell(ctx.applicationContext,command,timeoutMs=6_000)
    private fun ready(ctx:Context): Boolean {
        val r=shell(ctx,"echo dolphin_hud_permission_ready")
        return r.success && r.output.contains("dolphin_hud_permission_ready")
    }
    @JvmStatic fun isPortOpen():Boolean=NativeAdbClient.isPortOpen()
    @JvmStatic fun connect(ctx:Context, cb:Callback){
        if(!working.compareAndSet(false,true)){notify(cb,"이미 ADB 요청이 진행 중입니다.",true);return}
        Thread({
            try{
                if(!NativeAdbClient.isPortOpen()){
                    notify(cb,"PORT_CLOSED: 127.0.0.1:5555 연결 불가. 앱이 adbd를 임의로 활성화할 수 없습니다.",true)
                }else{
                    notify(cb,"로컬 ADB 인증 시도 중. 차량에서 RSA 승인창이 나타나면 허용하세요.")
                    val r=NativeAdbClient.waitForAuthorization(ctx,maxWaitMs=30_000L,
                       onProgress={m->notify(cb,m)})
                    notify(cb,if(r.success && r.output.contains("dolphin_hud_adb_ok"))
                        "ADB_READY: 차량 로컬 셸 인증 완료. 이제 'ADB 권한 적용'을 별도로 실행하세요."
                        else "ADB_AUTH_FAILED: "+r.message.take(220),true)
                }
            } catch(t:Throwable){notify(cb,"ADB 연결 오류: "+t.javaClass.simpleName+" "+t.message,true)}
            finally{working.set(false)}
        },"hud-adb-connect-ui").start()
    }
    @JvmStatic fun apply(ctx:Context, enableA11y:Boolean, cb:Callback){
        if(!working.compareAndSet(false,true)){notify(cb,"다른 ADB 작업이 진행 중입니다.",true);return}
        Thread({
            val out=mutableListOf<String>()
            try{
                if(!ready(ctx)){
                    notify(cb,"ADB_NOT_READY: 먼저 로컬 ADB 연결/RSA 승인이 필요합니다. 포트가 닫혀 있으면 동봉된 PC ADB 설정 스크립트를 사용하세요.",true)
                    return@Thread
                }
                val pkg=ctx.packageName
                val listener=ComponentName(ctx,NavNotificationService::class.java).flattenToString()
                val a11y=ComponentName(ctx,NavAccessibilityService::class.java).flattenToString()
                fun run(label:String,cmd:String){
                    val r=shell(ctx,cmd)
                    out += "$label: "+if(r.success)"명령 성공" else "거부/오류 "+r.message.take(130)
                }
                run("WRITE_SECURE_SETTINGS","pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
                if(Build.VERSION.SDK_INT<=30)run("위치(블루투스 검색)","pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
                if(Build.VERSION.SDK_INT>=31){
                    run("블루투스 CONNECT","pm grant $pkg android.permission.BLUETOOTH_CONNECT")
                    run("블루투스 SCAN","pm grant $pkg android.permission.BLUETOOTH_SCAN")
                }
                run("알림 접근","cmd notification allow_listener $listener")
                // Some Android 10 vehicle images omit cmd notification allow_listener.
                // Preserve other listeners and read back the result before claiming success.
                val nMgr=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if(!nMgr.isNotificationListenerAccessGranted(ComponentName(ctx,NavNotificationService::class.java))){
                    val prev=shell(ctx,"settings get secure enabled_notification_listeners")
                    if(prev.success){
                        val existing=prev.output.trim().takeUnless { it=="null" }.orEmpty()
                        val entries=existing.split(':').filter{it.isNotBlank()}.toMutableList()
                        if(!entries.any{ComponentName.unflattenFromString(it)==ComponentName.unflattenFromString(listener)})entries.add(listener)
                        val merged=entries.distinct().joinToString(":")
                        if(merged.matches(Regex("[A-Za-z0-9_./:$-]+")))
                            run("알림 접근 호환 적용","settings put secure enabled_notification_listeners '$merged'")
                        else out+="알림 접근 목록에 예상 외 문자가 있어 기존 목록을 수정하지 않았습니다."
                    }else out+="알림 접근 목록 조회 실패: 기존 설정 보호를 위해 호환 적용을 건너뜁니다."
                }
                if(enableA11y){
                    // Preserve other enabled services. If read fails, do not alter global settings.
                    val prev=shell(ctx,"settings get secure enabled_accessibility_services")
                    if(prev.success){
                        val existing=prev.output.trim().takeUnless{it=="null"}.orEmpty()
                        val entries=existing.split(':').filter{it.isNotBlank()}.toMutableList()
                        if(!entries.any{ComponentName.unflattenFromString(it)==ComponentName.unflattenFromString(a11y)})entries.add(a11y)
                        val merged=entries.distinct().joinToString(":")
                        if(merged.matches(Regex("[A-Za-z0-9_./:$-]+"))){
                            run("접근성 서비스 등록","settings put secure enabled_accessibility_services '$merged'")
                            run("접근성 활성화","settings put secure accessibility_enabled 1")
                        }else out+="접근성 기존 목록에 미확인 문자가 있어 수정 중지(기존 서비스 보호)"
                    }else out+="접근성 목록 읽기 실패: 다른 서비스를 보호하기 위해 수정하지 않았습니다."
                }
                val check=readStatus(ctx)
                out+="--- 실제 권한 상태 ---"
                out+=check
                out+="※ 권한 부여와 순정 TBT 방송 제공은 별도입니다. 실제 TBT 수신은 로그로 확인하세요."
                notify(cb,out.joinToString("\n"),true)
            }catch(t:Throwable){notify(cb,"ADB 적용 오류: "+t.javaClass.simpleName+" "+t.message+"\n"+out.joinToString("\n"),true)}
            finally{working.set(false)}
        },"hud-adb-permissions").start()
    }
    @JvmStatic fun audit(ctx:Context,cb:Callback){
        Thread({
            try{val prefix=if(ready(ctx))"ADB_READY" else "ADB_NOT_READY";notify(cb,prefix+"\n"+readStatus(ctx),true)}
            catch(t:Throwable){notify(cb,"권한 확인 실패: "+t.message,true)}
        },"hud-adb-audit").start()
    }
    @JvmStatic fun readStatus(ctx:Context):String{
        val listener=ComponentName(ctx,NavNotificationService::class.java)
        val accessibility=ComponentName(ctx,NavAccessibilityService::class.java)
        val notificationOk=try{
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationListenerAccessGranted(listener)
        }catch(_:Exception){false}
        val enabled=try{Settings.Secure.getString(ctx.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()}
                    catch(_:Exception){""}
        val a11yOk=enabled.split(':').any{ComponentName.unflattenFromString(it)==accessibility}
        val secureOk=ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==PackageManager.PERMISSION_GRANTED
        val fineOk=Build.VERSION.SDK_INT>=31 || ctx.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")==PackageManager.PERMISSION_GRANTED
        val btOk=Build.VERSION.SDK_INT<31 || (ctx.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")==PackageManager.PERMISSION_GRANTED && ctx.checkSelfPermission("android.permission.BLUETOOTH_SCAN")==PackageManager.PERMISSION_GRANTED)
        return "알림 리스너: ${if(notificationOk) "GRANTED" else "NOT_GRANTED"}\n"+
            "화면 접근성: ${if(a11yOk) "ENABLED" else "NOT_ENABLED"}\n"+
            "WRITE_SECURE_SETTINGS: ${if(secureOk) "GRANTED" else "NOT_GRANTED"}\n"+
            "블루투스/위치: ${if(fineOk&&btOk) "GRANTED" else "CHECK_REQUIRED"}\n"+
            "순정 내비 OEM TBT 권한/실제 송신: UNVERIFIED"
    }
}
