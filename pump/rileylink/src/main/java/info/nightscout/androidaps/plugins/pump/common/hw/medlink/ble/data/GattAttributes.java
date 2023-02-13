package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Dirceu on 11/10/20.
 */
public class GattAttributes {
    public static String SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static String GATT_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";

    //Riley didn't needed
    public static String CHARA_RADIO_VERSION = "30d99dc9-7c91-4295-a051-0a104d238cf2";

    private static Map<String, String> attributes;
    private static Map<String, String> attributesRileyLinkSpecific;

    // table of names for uuids
    static {
        attributes = new HashMap<>();

        attributes.put(SERVICE_UUID, "Device Information Service");
//        attributes.put(CHARA_GAP_NAME, "Name"); //
//        attributes.put(CHARA_GAP_NUM, "Number"); //
//
//        attributes.put(SERVICE_BATTERY, "Battery Service");
//
//        attributes.put(SERVICE_RADIO, "Radio Interface"); // a
//        attributes.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributes.put(GATT_UUID, "Data");
//        attributes.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
//        attributes.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
//        attributes.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
//        attributes.put(CHARA_RADIO_LED_MODE, "Led Mode");

        attributesRileyLinkSpecific = new HashMap<>();

        attributesRileyLinkSpecific.put(SERVICE_UUID, "Radio Interface"); // a
//        attributesRileyLinkSpecific.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributesRileyLinkSpecific.put(GATT_UUID, "Data");
//        attributesRileyLinkSpecific.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
//        attributesRileyLinkSpecific.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
//        attributesRileyLinkSpecific.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
//        attributesRileyLinkSpecific.put(CHARA_RADIO_LED_MODE, "Led Mode");
    }


    public static String lookup(UUID uuid) {
        return lookup(uuid.toString());
    }


    public static String lookup(String uuid) {
        return lookup(uuid, uuid);
    }


    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }


    // we check for specific UUID (Radio ones, because thoose seem to be unique
    public static boolean isRileyLink(UUID uuid) {
        return attributesRileyLinkSpecific.containsKey(uuid.toString());
    }
}
