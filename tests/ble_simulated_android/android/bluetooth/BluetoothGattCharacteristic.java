package android.bluetooth;import java.util.*;public class BluetoothGattCharacteristic{
 public static final int PROPERTY_WRITE=8,PROPERTY_WRITE_NO_RESPONSE=4,PROPERTY_NOTIFY=16,WRITE_TYPE_DEFAULT=2,WRITE_TYPE_NO_RESPONSE=1;
 private final UUID uuid;private final int props; private byte[] value;private int writeType;
 public BluetoothGattCharacteristic(UUID u,int p){uuid=u;props=p;}public UUID getUuid(){return uuid;}public int getProperties(){return props;}
 public void setValue(byte[] v){value=v.clone();}public byte[] getValue(){return value;}public void setWriteType(int t){writeType=t;}
 public BluetoothGattDescriptor getDescriptor(UUID u){return new BluetoothGattDescriptor(u);}
}