package com.dolphin.oemhud;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
public final class EndToEndRuntimeTest {
  private static int passed;
  private static void ok(boolean b,String label){if(!b)throw new AssertionError(label);passed++;System.out.println("PASS INTEGRATION "+label);}
  private static Intent tbt(int icon,String distance){return new Intent().setAction("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE",10001).putExtra("NEW_ICON",icon).putExtra("SEG_REMAIN_DIS_AUTO",distance);}
  public static void main(String[] args){
    Handler.reset();BluetoothGatt.automaticConnection=true;BluetoothGatt.discoverReply=true;BluetoothGatt.writeReply=true;BluetoothGatt.hasWriter=true;
    BridgeService b=new BridgeService();b.getSharedPreferences(BridgeLog.STORE,0).edit().putBoolean("enabled",true).putString("hud_mac","AA:BB:CC:11:22:33").apply();
    b.onCreate();b.onStartCommand(null,0,1);Handler.advance(0);
    int start=BluetoothGatt.writes;
    b.registered.onReceive(b,tbt(3,"1,200m"));
    ok(BluetoothGatt.writes==start+1,"OEM broadcast reaches REAL FFF2 GATT writer");
    ok(MconHudQProtocol.hex(BluetoothGatt.lastValue).equals("194D010500050004B02F"),"OEM grouped distance + turn code encoded exactly");
    ok(b.getSharedPreferences(BridgeLog.STORE,0).getInt("hud_tx_completed",0)==1,"onCharacteristicWrite completed frame");
    b.registered.onReceive(b,tbt(3,"1,200m"));
    ok(BluetoothGatt.writes==start+1,"same TBT deduplicated");
    b.registered.onReceive(b,tbt(11,"100m"));
    ok(BluetoothGatt.writes==start+1,"unverified turn icon fails closed");
    b.getSharedPreferences(BridgeLog.STORE,0).edit().putBoolean("enabled",false).apply();
    b.registered.onReceive(b,tbt(2,"50m"));
    ok(BluetoothGatt.writes==start+1,"user stop blocks OEM broadcasts");
    b.onStartCommand(new Intent().setAction(BridgeService.ACTION_DISCONNECT),0,2);
    ok(b.getSharedPreferences(BridgeLog.STORE,0).getString("hud_mac",null)==null,"disconnect clears auto-connect MAC");
    b.onDestroy();
    System.out.println("REAL SERVICE + REAL CONNECTOR "+passed+" PASS");
  }
}
