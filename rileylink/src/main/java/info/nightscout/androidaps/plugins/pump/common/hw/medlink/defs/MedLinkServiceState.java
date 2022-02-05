package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;


import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R;

/**
 * Created by andy on 14/05/2018.
 */

public enum MedLinkServiceState {

    NotStarted(R.string.rileylink_state_not_started), //

    // Bluetooth
    BluetoothInitializing(R.string.rileylink_state_bt_init), // (S) init BT (if error no BT interface -> Disabled, BT
                                                             // not enabled -> BluetoothError)
    // BluetoothNotAvailable, // (E) BT not available, would happen only if device has no BT
    BluetoothError(R.string.rileylink_state_bt_error), // (E) if BT gets disabled ( -> EnableBluetooth)
    BluetoothReady(R.string.rileylink_state_bt_ready), // (OK)

    // RileyLink
    MedLinkInitializing(R.string.medlink_state_rl_init), // (S) start Gatt discovery (OK -> RileyLinkReady, Error ->
                                                             // BluetoothEnabled) ??
    MedLinkError(R.string.medlink_state_rl_error), // (E)
    MedLinkReady(R.string.medlink_state_rl_ready), // (OK) if tunning was already done we go to PumpConnectorReady

    // Tunning
    TuneUpDevice(R.string.medlink_state_pc_tune_up), // (S)
    PumpConnectorError(R.string.rileylink_state_pc_error), // either TuneUp Error or pump couldn't not be contacted
                                                           // error
    PumpConnectorReady(R.string.rileylink_state_connected), // (OK) RileyLink Ready for Pump Communication

    // Initializing, // get all parameters required for connection (if not possible -> Disabled, if sucessful ->
    // EnableBluetooth)

    // EnableBlueTooth, // enable BT (if error no BT interface -> Disabled, BT not enabled -> BluetoothError)
    // BlueToothEnabled, // -> InitializeRileyLink
    // RileyLinkInitialized, //

    // RileyLinkConnected, // -> TuneUpPump (on 1st), else PumpConnectorReady

    // PumpConnected, //
    ;

    int resourceId;

    MedLinkServiceState(int resourceId) {
        this.resourceId = resourceId;
    }

    public boolean isReady() {
        return (this == PumpConnectorReady);
    }

    public int getResourceId() {
        return this.resourceId;
    }

    public boolean isConnecting() {
        return (this == MedLinkServiceState.BluetoothInitializing || //
            // this == RileyLinkServiceState.BluetoothError || //
            this == MedLinkServiceState.BluetoothReady || //
            this == MedLinkServiceState.MedLinkInitializing || //
        this == MedLinkReady
        // this == RileyLinkServiceState.RileyLinkBLEError
        );
    }

    public boolean isError() {
        return (this == MedLinkServiceState.BluetoothError || //
        // this == RileyLinkServiceState.PumpConnectorError || //
        this == MedLinkServiceState.MedLinkError);
    }
}
