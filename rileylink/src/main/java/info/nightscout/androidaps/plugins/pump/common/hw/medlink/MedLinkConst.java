package info.nightscout.androidaps.plugins.pump.common.hw.medlink;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R;


/**
 * Created by dirceu ou 18/09/20
 * Copied from RileyLinkConst
 */

public class MedLinkConst {

    static final String Prefix = "AAPS.MedLink.";
    public static String FREQUENCY_CALIBRATION_SUCCESS="medlink calibration success";
    public static String DEVICE_MAC_ADDRESS=null;
    public static final List<String> DEVICE_NAME = Collections.unmodifiableList(
            new ArrayList<String>() {{
                add("MEDLINK");
                add("MED-LINK");
                add("MED-LINK-2");
                add("MED-LINK-3");
                add("HMSoft");
            }});

    public static class Intents {

        public static final String MedLinkReady = Prefix + "MedLink_Ready";
        public static final String MedLinkReadyServiceDiscovered = Prefix + "MedLink_ServiceDiscovered";
        public static final String CommandCompleted = Prefix + "Command_Completed";
        public static final String RileyLinkGattFailed = Prefix + "RileyLink_Gatt_Failed";
        public static final String MedLinkConnected = Prefix + "MedLink_Connected";
        public static final String BluetoothConnected = Prefix + "Bluetooth_Connected";
        public static final String BluetoothReconnected = Prefix + "Bluetooth_Reconnected";
        public static final String BluetoothDisconnected = Prefix + "Bluetooth_Disconnected";
        public static final String MedLinkDisconnected = Prefix + "MedLink_Disconnected";

        public static final String MedLinkNewAddressSet = Prefix + "NewAddressSet";

        public static final String INTENT_NEW_rileylinkAddressKey = Prefix + "INTENT_NEW_rileylinkAddressKey";
        public static final String INTENT_NEW_pumpIDKey = Prefix + "INTENT_NEW_pumpIDKey";
        public static final String MedLinkDisconnect = Prefix + "MedLink_Disconnect";

        public static final String  MedLinkConnectionError = Prefix + "MedLink_Disconnect";
    }

    public static class Prefs {

        //public static final String PrefPrefix = "pref_rileylink_";
        //public static final String RileyLinkAddress = PrefPrefix + "mac_address"; // pref_rileylink_mac_address
        public static final int MedLinkAddress = R.string.key_medlink_mac_address;
        public static final int MedLinkName = R.string.key_rileylink_name;

        public static final String LastGoodDeviceCommunicationTime = Prefix + "lastGoodDeviceCommunicationTime";
        public static final String LastGoodDeviceFrequency = Prefix + "LastGoodDeviceFrequency";
        public static final int Encoding = R.string.key_medtronic_encoding;
    }

    public static class IPC {

        // needs to br renamed (and maybe removed)
        public static final String MSG_PUMP_quickTune = Prefix + "MSG_PUMP_quickTune";
        public static final String MSG_PUMP_tunePump = Prefix + "MSG_PUMP_tunePump";

        public static final String MSG_ServiceCommand = Prefix + "MSG_ServiceCommand";
    }

}
