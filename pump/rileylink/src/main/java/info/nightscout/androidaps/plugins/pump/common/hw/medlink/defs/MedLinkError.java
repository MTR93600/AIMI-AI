package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;


import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;

/**
 * Created by andy on 14/05/2018.
 */

public enum MedLinkError {

    // Configuration

    // BT
    NoBluetoothAdapter(R.string.rileylink_error_no_bt_adapter), //
    BluetoothDisabled(R.string.rileylink_error_bt_disabled), //

    // RileyLink
    MedLinkUnreachable(R.string.medlink_error_unreachable), //
    DeviceIsNotMedLink(R.string.medlink_error_not_rl), //

    // Device
    TuneUpOfDeviceFailed(R.string.rileylink_error_tuneup_failed), //
    NoContactWithDevice(R.string.rileylink_error_pump_unreachable, R.string.rileylink_error_pod_unreachable), //
    ;

    int resourceId;
    Integer resourceIdPod;


    MedLinkError(int resourceId) {
        this.resourceId = resourceId;
    }


    MedLinkError(int resourceId, int resourceIdPod) {
        this.resourceId = resourceId;
        this.resourceIdPod = resourceIdPod;
    }


    public int getResourceId(RileyLinkTargetDevice targetDevice) {
        if (this.resourceIdPod != null) {

            return targetDevice == RileyLinkTargetDevice.MedtronicPump ? //
            this.resourceId
                : this.resourceIdPod;
        } else {
            return this.resourceId;
        }
    }

}
