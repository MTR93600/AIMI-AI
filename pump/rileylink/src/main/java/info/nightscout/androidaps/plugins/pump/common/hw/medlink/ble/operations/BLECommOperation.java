package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations;

import android.bluetooth.BluetoothGatt;

import java.util.UUID;
import java.util.concurrent.Semaphore;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;

/**
 * Created by dirceu on 12/27/20.
 * copied from @{link {@link info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperation}}
 */
public abstract class BLECommOperation {

    public boolean timedOut = false;
    public boolean interrupted = false;
    protected byte[] value;
    protected BluetoothGatt gatt;
    protected Semaphore operationComplete = new Semaphore(0, true);


    // This is to be run on the main thread
    public abstract void execute(MedLinkBLE comm);


    public void gattOperationCompletionCallback(UUID uuid, byte[] value) {
    }


    public int getGattOperationTimeout_ms() {
        return 82000;
    }


    public byte[] getValue() {
        return value;
    }
}
