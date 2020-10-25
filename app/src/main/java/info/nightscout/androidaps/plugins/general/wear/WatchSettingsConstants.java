package info.nightscout.androidaps.plugins.general.wear;

// Settings (On/Off) sent to watch or setting mask received from watch sent by a long value
// See here the bit number of each setting in this long value (64 bits available)
// These constant could be used for setting menu generation "key=wearos_xx" or "key=tizen_xx" with xx = bit number in pref_wear.xml file

public class WatchSettingsConstants {
    public static final int WEAR_CONTROL_SETTING = 0;

    public static final int BG_SECTION = 1;
    public static final int SHOW_BG_SETTING = 1;
    public static final int SHOW_BG_ARROW_SETTING = 2;
    public static final int SHOW_DELTA_SETTING = 3;
    public static final int SHOW_15MIN_DELTA_SETTING = 4;
    public static final int SHOW_40MIN_DELTA_SETTING = 5;
    public static final int SHOW_DETAILLED_DELTA_SETTING = 6;
    // one bit available (or 2 if we delete 40 Min delta setting), for Steampunk Watchface that have 4 different settings...

    public static final int IOB_COB_BGI_SECTION = 8;
    public static final int SHOW_IOB_SETTING = 8;
    public static final int SHOW_DETAILLED_IOB_SETTING = 9;
    public static final int SHOW_COB_SETTING = 10;
    public static final int SHOW_BGI_SETTING = 11;

    public static final int BATTERY_SECTION = 14;
    public static final int SHOW_PHONE_BATTERY_SETTING = 14;
    public static final int SHOW_PUMP_BATTERY_SETTING = 15;
    public static final int SHOW_SENSOR_BATTERY_SETTING = 16;
    public static final int SHOW_RIG_BATTERY_SETTING = 17;

    public static final int OTHER_SECTION = 20;
    public static final int SHOW_BASAL_RATE_SETTING = 21;
    public static final int SHOW_LOOP_STATUS_SETTING = 21;
    public static final int SHOW_AGO_SETTING = 22;

    public static final int GRAPH_SECTION = 24;
    public static final int SHOW_PREDICTION_LINES_SETTING = 24;
    public static final int SHOW_CARB_POINT_SETTING = 25;
    public static final int SHOW_BOLUS_POINT_SETTING = 26;
    public static final int SHOW_SMB_AS_BOLUS_SETTING = 27;
    public static final int SHOW_PROFILE_SETTING = 28;
    public static final int SHOW_BASAL_CURV_SETTING = 29;
    // here we have 2 available bits for time scale in graph (but not sure it's necessary, I never modify it from setting menu...)

    public static final int TIME_SECTION = 32;
    public static final int SHOW_SECONDS_SETTING = 32;
    public static final int SHOW_DAY_NAME_SETTING = 33;         // Sunday - Saturday
    public static final int SHOW_SHORT_DATE_SETTING = 34;       // 21/05/2020 => 21
    public static final int SHOW_DATE_SETTING = 35;             // 21/05/2020 => 21/05
    public static final int SHOW_LONG_DATE_SETTING = 36;        // 21/05/2020 => 21/05/20 or 21/05/2020 (just a setting sent to watch that decide according to layout)

    public static final int COLOR_SECTION = 38;
    public static final int SHOW_DARK_BACKGROUND_SETTING = 38;  // Black or White Background in WearOS watches
    public static final int SHOW_DARK_DIVIDER_SETTING = 39;     // Close to Matching Divider
    // 8 bits available for may be more colors (Red, Blue, green, Cyan,...)

    public static final int TEXT_SECTION = 48;
    public static final int SHOW_BIG_NUMBER_SETTING = 48;       // could be BG Size, or global text size...

    // Circle Watch faces (BG history, animation ...)

}
