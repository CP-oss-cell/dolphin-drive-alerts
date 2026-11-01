package android.bluetooth;import android.content.Context;public class BluetoothDevice{
 public static final int TRANSPORT_LE=2;private final String name,address; public static BluetoothGatt latest;
 public BluetoothDevice(String n,String a){name=n;address=a;}public String getName(){return name;}public String getAddress(){return address;}
 public BluetoothGatt connectGatt(Context c,boolean auto,BluetoothGattCallback cb,int transport){latest=new BluetoothGatt(cb);if(BluetoothGatt.automaticConnection){BluetoothGatt active=latest;new android.os.Handler(android.os.Looper.getMainLooper()).post(active::simulateConnected);}return latest;}
}