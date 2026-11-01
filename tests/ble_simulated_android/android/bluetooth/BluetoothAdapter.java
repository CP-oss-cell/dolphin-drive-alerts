package android.bluetooth;import android.bluetooth.le.BluetoothLeScanner;import java.util.*; public class BluetoothAdapter{
 public static final String ACTION_STATE_CHANGED="STATE";public static final String EXTRA_STATE="state";public static final int STATE_ON=12;
 private static final BluetoothAdapter INSTANCE=new BluetoothAdapter();private final BluetoothLeScanner scanner=new BluetoothLeScanner();
 public static BluetoothAdapter getDefaultAdapter(){return INSTANCE;}public boolean isEnabled(){return true;}
 public Set<BluetoothDevice> getBondedDevices(){return new HashSet<>();} public BluetoothLeScanner getBluetoothLeScanner(){return scanner;}
 public BluetoothDevice getRemoteDevice(String mac){return new BluetoothDevice("TPLAY HUD Q",mac);}
}