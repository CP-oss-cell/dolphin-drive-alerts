package com.dolphin.oemhud;
import android.bluetooth.*;import android.content.*;import android.os.Handler;
public final class BleRuntimeTest{
 static int count=0;static void ok(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS BLE "+s);count++;}
 static final String MAC="AA:BB:CC:11:22:33";
 static NavCue cue(){return new NavCue("sim","우회전",300,"",3,0,"");}
 static Context ctx(){Context c=new Context();c.getSharedPreferences(BridgeLog.STORE,0).edit().putString("hud_mac",MAC).apply();return c;}
 public static void main(String[]a){
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;BluetoothGatt.writeReply=true;
  Context c=ctx();HudConnector h=new HudConnector(c);h.connect(new BluetoothDevice("ARIA HUD Q",MAC),s->{});Handler.advance(0);
  ok(h.isReady(),"connected GATT FFF2");
  ok(h.sendTurn(cue()),"enqueue right 300");
  ok(BluetoothGatt.writes==1,"real connector invoked characteristic write");
  ok(MconHudQProtocol.hex(BluetoothGatt.lastValue).equals("194D0105000500012C2F"),"wire packet content");
  ok(c.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_completed",0)==1,"write callback recorded");
  h.disconnect();ok(!h.isReady(),"manual disconnect shuts down");
  Handler.reset();BluetoothGatt.automaticConnection=false;BluetoothGatt.discoverReply=true;
  HudConnector h2=new HudConnector(ctx());int opened=BluetoothGatt.opens;
  h2.connect(new BluetoothDevice("HUD Q",MAC),s->{});opened=BluetoothGatt.opens;
  ok(!h2.isReady(),"connected pending no callback");
  Handler.advance(13000);ok(BluetoothGatt.opens==opened,"connect timeout schedules reconnect");
  Handler.advance(1400);ok(BluetoothGatt.opens>opened,"connect timeout reopened GATT");
  h2.disconnect();
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=false;
  HudConnector h3=new HudConnector(ctx());opened=BluetoothGatt.opens;
  h3.connect(new BluetoothDevice("HUD Q",MAC),s->{});Handler.advance(0);opened=BluetoothGatt.opens;
  Handler.advance(11000);ok(!h3.isReady(),"service timeout detected");
  Handler.advance(1500);ok(BluetoothGatt.opens>opened,"service timeout auto-reconnect");
  h3.disconnect();
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;
  HudConnector h4=new HudConnector(ctx());h4.connect(new BluetoothDevice(null,MAC),s->{});Handler.advance(0);
  ok(h4.isReady(),"saved HUD MAC accepted without transient BLE name");h4.disconnect();
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;BluetoothGatt.writeReply=false;
  Context c5=ctx();HudConnector h5=new HudConnector(c5);h5.connect(new BluetoothDevice("HUD Q",MAC),s->{});Handler.advance(0);
  int before=BluetoothGatt.writes;h5.sendTurn(cue());
  Handler.advance(15500);
  ok(BluetoothGatt.writes-before==3,"no GATT ack triggers bounded three attempts");
  ok(c5.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_errors",0)>=3,"GATT timeout errors recorded");
  h5.disconnect();BluetoothGatt.writeReply=true;
  Handler.reset();BluetoothGatt.automaticConnection=false;
  HudConnector h6=new HudConnector(ctx());h6.connect(new BluetoothDevice("HUD Q",MAC),s->{});
  int stoppedAt=BluetoothGatt.opens;h6.disconnect();Handler.advance(30000);
  ok(BluetoothGatt.opens==stoppedAt,"manual disconnect cancels watchdog/reconnect");
  Handler.reset();BluetoothGatt.automaticConnection=false;BluetoothGatt.discoverReply=true;BluetoothGatt.writeReply=true;
  HudConnector h7=new HudConnector(ctx());h7.connect(new BluetoothDevice("HUD Q",MAC),s->{});
  int waitingWrites=BluetoothGatt.writes;
  ok(!h7.sendTurn(cue()),"guidance deferred while disconnected");
  BluetoothDevice.latest.simulateConnected();
  ok(h7.isReady(),"pending GATT later becomes ready");
  ok(BluetoothGatt.writes==waitingWrites+1,"deferred cue flushed after connection");h7.disconnect();
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;BluetoothGatt.writeReply=false;
  Context c8=ctx();HudConnector h8=new HudConnector(c8);
  h8.connect(new BluetoothDevice("TPLAY HUD Q",MAC),st->{});Handler.advance(0);
  ok(h8.isReady(),"late-write scenario connected");
  h8.sendTurn(cue());BluetoothGatt original=BluetoothDevice.latest;
  Handler.advance(2500);
  ok(original.closed,"ACK timeout closes original GATT instead of reusing it");
  int completedBefore=c8.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_completed",0);
  original.simulateLateWrite();
  ok(c8.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_completed",0)==completedBefore,"stale GATT callback ignored");
  BluetoothGatt.writeReply=true;Handler.advance(1100);
  ok(c8.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_completed",0)==completedBefore+1,"fresh session ACK completes queued turn");
  h8.disconnect();
  Handler.reset();BluetoothGatt.hasWriter=false;BluetoothGatt.automaticConnection=true;
  Context c9=ctx();HudConnector h9=new HudConnector(c9);
  h9.connect(new BluetoothDevice("ARIA HUD Q",MAC),st->{});Handler.advance(0);
  ok(!h9.isReady(),"missing FFF2 characteristic refuses HUD TX");
  ok(c9.getSharedPreferences(BridgeLog.STORE,0).getString("hud_status","").contains("FFF2"),"missing characteristic shown in status");
  h9.disconnect();BluetoothGatt.hasWriter=true;
  Handler.reset();BluetoothGatt.automaticConnection=false;
  HudConnector h10=new HudConnector(ctx());h10.connect(new BluetoothDevice("HUD Q",MAC),st->{});
  int writesBefore=BluetoothGatt.writes;
  h10.sendTurn(cue());Handler.advance(11000);
  BluetoothDevice.latest.simulateConnected();
  ok(h10.isReady(),"late GATT connection ready");
  ok(BluetoothGatt.writes==writesBefore,"stale TBT after 10s never transmitted");
  h10.disconnect();
  ok(!HudConnector.isSupportedName("EV Dashboard"),"non-HUD BLE name blocked");
  ok(HudConnector.isSupportedName("ARIA_HUD-Q"),"HUDQ variant name allowed");
  // Descriptor write must be completed before any navigation packet write.
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;
  BluetoothGatt.writeReply=true;BluetoothGatt.hasNotify=true;BluetoothGatt.descriptorReply=false;
  HudConnector h11=new HudConnector(ctx());h11.connect(new BluetoothDevice("ARIA HUD Q",MAC),st->{});Handler.advance(0);
  ok(!h11.isReady(),"CCCD pending prevents premature FFF2 write");
  int at11=BluetoothGatt.writes;
  ok(!h11.sendTurn(cue()),"cue queued while CCCD pending");
  ok(BluetoothGatt.writes==at11,"no characteristic write overlaps descriptor write");
  BluetoothDevice.latest.simulateDescriptorReply();
  ok(h11.isReady(),"CCCD callback transitions ready");
  ok(BluetoothGatt.writes==at11+1,"queued cue sent only after CCCD completion");
  h11.disconnect();
  Handler.reset();BluetoothGatt.descriptorReply=false;
  HudConnector h12=new HudConnector(ctx());h12.connect(new BluetoothDevice("ARIA HUD Q",MAC),st->{});Handler.advance(0);
  int at12=BluetoothGatt.opens;
  Handler.advance(5000);ok(!h12.isReady(),"CCCD timeout not treated as success");
  Handler.advance(1400);ok(BluetoothGatt.opens>at12,"CCCD timeout reconnects with fresh GATT");
  h12.disconnect();BluetoothGatt.hasNotify=false;BluetoothGatt.descriptorReply=true;
  // New: actual HUD buzzer and brightness commands must use the same serialized GATT writer.
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;
  BluetoothGatt.writeReply=true;BluetoothGatt.hasNotify=false;
  Context c13=ctx();HudConnector h13=new HudConnector(c13);
  h13.connect(new BluetoothDevice("ARIA HUD Q",MAC),st->{});Handler.advance(0);
  int w13=BluetoothGatt.writes;
  ok(h13.sendSetting(HudSettingsProtocol.buzzer(true,0)),"buzzer setting accepted");
  ok(BluetoothGatt.writes==w13+1,"buzzer uses live FFF2 GATT write");
  ok(MconHudQProtocol.hex(BluetoothGatt.lastValue).equals("194D080201032F"),"buzzer low encoded correctly");
  ok(h13.sendSetting(HudSettingsProtocol.brightness(2,0)),"brightness setting accepted");
  ok(MconHudQProtocol.hex(BluetoothGatt.lastValue).equals("194D070202002F"),"day/night brightness frame encoded");
  ok(!h13.sendSetting(new byte[]{0x19,0x4d,0x08,0x02,1,2,0x2f}),"unverified setting frame rejected");
  h13.disconnect();
  Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.writeReply=true;
  Context c14=ctx();c14.getSharedPreferences(BridgeLog.STORE,0).edit()
       .putBoolean("hud_buzzer_saved",true).putBoolean("hud_buzzer_enabled",true).putInt("hud_buzzer_level",2)
       .putBoolean("hud_brightness_saved",true).putBoolean("hud_brightness_auto",true)
       .putInt("hud_brightness_day",2).putInt("hud_brightness_night",0).apply();
  int w14=BluetoothGatt.writes;
  HudConnector h14=new HudConnector(c14);h14.connect(new BluetoothDevice("HUD Q",MAC),st->{});Handler.advance(100);
  ok(BluetoothGatt.writes==w14+2,"saved buzzer and brightness reapplied after BLE reconnect");
  ok(MconHudQProtocol.hex(BluetoothGatt.lastValue).equals("194D070202002F"),"last restored control is brightness frame");
  h14.disconnect();
    System.out.println("BLE RUNTIME "+count+" PASS");
 }
}
