package android.bluetooth;import java.util.*;
public class BluetoothGatt{
 public static final int GATT_SUCCESS=0;
 public static int opens=0,writes=0;public static byte[] lastValue;
 public static boolean automaticConnection=true;public static boolean discoverReply=true,writeReply=true,hasWriter=true,hasNotify=false,descriptorReply=true;
 private final BluetoothGattCallback callback;public boolean closed=false;private BluetoothGattDescriptor lastDescriptor;
 public BluetoothGatt(BluetoothGattCallback cb){callback=cb;opens++;}
 public void simulateConnected(){callback.onConnectionStateChange(this,GATT_SUCCESS,BluetoothProfile.STATE_CONNECTED);}
 public void simulateDisconnected(){callback.onConnectionStateChange(this,8,BluetoothProfile.STATE_DISCONNECTED);}
 public void simulateLateWrite(){callback.onCharacteristicWrite(this,new BluetoothGattCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),BluetoothGattCharacteristic.PROPERTY_WRITE),GATT_SUCCESS);}
 public boolean discoverServices(){if(discoverReply)callback.onServicesDiscovered(this,GATT_SUCCESS);return true;}
 public List<BluetoothGattService> getServices(){
   BluetoothGattService s=new BluetoothGattService();
   if(hasWriter)s.add(new BluetoothGattCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),BluetoothGattCharacteristic.PROPERTY_WRITE));
   if(hasNotify)s.add(new BluetoothGattCharacteristic(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),BluetoothGattCharacteristic.PROPERTY_NOTIFY));
   return Arrays.asList(s);
 }
 public boolean setCharacteristicNotification(BluetoothGattCharacteristic c,boolean enabled){return true;}
 public boolean writeDescriptor(BluetoothGattDescriptor d){lastDescriptor=d;if(descriptorReply)callback.onDescriptorWrite(this,d,0);return true;}
 public void simulateDescriptorReply(){if(lastDescriptor!=null)callback.onDescriptorWrite(this,lastDescriptor,0);}
 public boolean writeCharacteristic(BluetoothGattCharacteristic c){writes++;lastValue=c.getValue();if(writeReply)callback.onCharacteristicWrite(this,c,0);return true;}
 public void disconnect(){}public void close(){closed=true;}
}
