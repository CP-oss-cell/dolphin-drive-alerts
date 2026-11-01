package android.bluetooth;import java.util.*;public class BluetoothGattService{
 private final List<BluetoothGattCharacteristic> chars=new ArrayList<>();public void add(BluetoothGattCharacteristic c){chars.add(c);}public List<BluetoothGattCharacteristic> getCharacteristics(){return chars;}
}