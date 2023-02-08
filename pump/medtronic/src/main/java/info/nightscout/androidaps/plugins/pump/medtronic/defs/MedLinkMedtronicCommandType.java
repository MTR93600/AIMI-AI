package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.R;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.MessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpAckMessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.UnknownMessageBody;

/**
 * Taken from GNU Gluco Control diabetes management software (ggc.sourceforge.net)
 * <p>
 * Description: Medtronic Commands (Pump and CGMS) for all 512 and later models (just 5xx)
 * <p>
 * Link to original/unmodified file:
 * https://sourceforge.net/p/ggc/code/HEAD/tree/trunk/ggc-plugins/ggc-plugins-base/src/
 * main/java/ggc/plugin/device/impl/minimed/enums/MinimedCommandType.java
 * <p>
 * A lot of stuff has been removed because it is not needed anymore (historical stuff from CareLink
 * and Carelink USB communication.
 * <p>
 * Author: Andy {andy@atech-software.com}
 *
 * used by medlink
 */
public enum MedLinkMedtronicCommandType implements Serializable // , MinimedCommandTypeInterface
{

    InvalidCommand(0, "Invalid Command", null, null,
            MedLinkCommandType.NoCommand), //

    // Pump Responses (9)
    CommandACK(),
//            0x06, "ACK - Acknowledge", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), //
//    CommandNAK(0x15, "NAK - Not Acknowledged", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), //

    // All (8)
//    PushAck(91, "Push ACK", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(2)), //
//
//    PushEsc(91, "Push Esc", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(1)), //
//
//    PushButton(0x5b, "Push Button", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), // 91
//
    RFPowerOn(93, "RF Power On", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(
            1, 10)), //
//
//    RFPowerOff(93, "RF Power Off", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(
//            0, 0)), //

    SuspendActivate(77, "Set Suspend", MedtronicDeviceType.All,
     MinimedCommandParameterType.NoParameters, MedLinkCommandType.StopStartPump ), //a

    Bolus(77, "Set Suspend", MedtronicDeviceType.All,
            MinimedCommandParameterType.FixedParameters, getByteArray()), //a

    // SetSuspend(77, "Set Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All,
    // MinimedCommandParameterType.FixedParameters, getByteArray(1)), //

    // CancelSuspend(77, "Cancel Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All,
    // MinimedCommandParameterType.FixedParameters, getByteArray(0)), //

//    PumpState(131, "Pump State", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), //
//
//    ReadPumpErrorStatus(117, "Pump Error Status", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), //

    // 511 (InitCommand = 2, Config 7, Data = 1(+3)
//    DetectBolus(75, "Detect Bolus", MedtronicDeviceType.Medtronic_511, MinimedCommandParameterType.FixedParameters, getByteArray(
//        0, 0, 0)), //

    // RemoteControlIds(118, "Remote Control Ids", MinimedTargetType.PumpConfiguration_NA, MedtronicDeviceType.All,
    // MinimedCommandParameterType.NoParameters), //

    // FirmwareVersion(116, "Firmware Version", MinimedTargetType.InitCommand, MedtronicDeviceType.All,
    // MinimedCommandParameterType.NoParameters), //

    // PumpId(113, "Pump Id", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.All,
    // MinimedCommandParameterType.NoParameters), // init

    SetRealTimeClock(0x40, "Set Pump Time", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters, //
            0), //
//
    GetRealTimeClock(112, "Get Pump Time", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters, //
            7, R.string.medtronic_cmd_desc_get_time, MedLinkCommandType.GetState), // 0x70
//
    GetBatteryStatus(0x72, "Get Battery Status", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters), //
     GetBattery((byte) 0x72), //

    GetRemainingInsulin(0x73, "Read Remaining Insulin", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters, 2), // 115

    SetBolus(0x42, "Set Bolus", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters, //
            0, R.string.medtronic_cmd_desc_set_bolus, MedLinkCommandType.Bolus), // 66
//
    // 512
    //TODO not needed
    ReadTemporaryBasal(0x98, "Read Temporary Basal", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
            5, R.string.medtronic_cmd_desc_get_tbr,
        MedLinkCommandType.NoCommand), // 152
//
//    SetTemporaryBasal(76, "Set Temporay Basal", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
//            0, R.string.medtronic_cmd_desc_set_tbr),

    // 512 Config
    PumpModel(141, "Pump Model", MedtronicDeviceType.Medtronic_512andHigher,
            MinimedCommandParameterType.NoParameters, //
            5, R.string.medtronic_cmd_desc_get_model,MedLinkCommandType.Connect), // 0x8D

    // BGTargets_512(140, "BG Targets", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512_712,
    // MinimedCommandParameterType.NoParameters), //

    // BGUnits(137, "BG Units", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //

    // Language(134, "Language", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //

    Settings_512(145, "Configuration", MedtronicDeviceType.Medtronic_512_712, MinimedCommandParameterType.NoParameters, //
            64, 1, 18, R.string.medtronic_cmd_desc_get_settings), //

    // BGAlarmClocks(142, "BG Alarm Clocks", MinimedTargetType.PumpConfiguration,
    // MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters), //

    // BGAlarmEnable(151, "BG Alarm Enable", MinimedTargetType.PumpConfiguration,
    // MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters), //

    // BGReminderEnable(144, "BG Reminder Enable", MinimedTargetType.PumpConfiguration,
    // MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters), //

    // ReadInsulinSensitivities(0x8b, "Read Insulin Sensitivities", MinimedTargetType.PumpConfiguration,
    // MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters), // 139

    // 512 Data
    GetHistoryData(128, "Get History", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.SubCommands, //
            1024, 16, 1024, R.string.medtronic_cmd_desc_get_history), // 0x80
//
    GetBasalProfileSTD(146, "Get Profile Standard", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
            64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile, MedLinkCommandType.GetState), // 146
//
    GetBasalProfileA(147, "Get Profile A", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
            64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile),

    GetBasalProfileB(148, "Get Profile B", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
            64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile), // 148

//    SetBasalProfileSTD(0x6f, "Set Profile Standard", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
//            64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile), // 111
//
//    SetBasalProfileA(0x30, "Set Profile A", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
//            64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile), // 48

//    SetBasalProfileB(0x31, "Set Profile B", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters, //
//            64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile), // 49

    // 515
    PumpStatus(206, "Pump Status", MedtronicDeviceType.Medtronic_515andHigher,
            MinimedCommandParameterType.NoParameters, MedLinkCommandType.GetState), // PumpConfiguration

    Settings(192, "Configuration", MedtronicDeviceType.Medtronic_515andHigher, MinimedCommandParameterType.NoParameters, //
            64, 1, 21, R.string.medtronic_cmd_desc_get_settings), //

    // 522
//    SensorSettings_522(153, "Sensor Configuration", MedtronicDeviceType.Medtronic_522andHigher, MinimedCommandParameterType.NoParameters), //
//
//    GlucoseHistory(154, "Glucose History", MedtronicDeviceType.Medtronic_522andHigher, MinimedCommandParameterType.SubCommands, 1024, 32, 0, null), //

    // 523
//    SensorSettings(207, "Sensor Configuration", MedtronicDeviceType.Medtronic_523andHigher, MinimedCommandParameterType.NoParameters), //
//
    // 553
    // 554

    // var MESSAGES = {
    // READ_TIME : 0x70,
    // READ_BATTERY_STATUS: 0x72,
    // READ_HISTORY : 0x80,
    // READ_CARB_RATIOS : 0x8A,
    // READ_INSULIN_SENSITIVITIES: 0x8B,
    // READ_MODEL : 0x8D,
    // READ_PROFILE_STD : 0x92,
    // READ_PROFILE_A : 0x93,
    // READ_PROFILE_B : 0x94,
    // READ_CBG_HISTORY: 0x9A,
    // READ_ISIG_HISTORY: 0x9B,
    // READ_CURRENT_PAGE : 0x9D,
    // READ_BG_TARGETS : 0x9F,
    // READ_SETTINGS : 0xC0, 192
    // READ_CURRENT_CBG_PAGE : 0xCD
    // };

    // Fake Commands

//    CancelTBR(),
    ;

    static Map<String, MedLinkMedtronicCommandType> mapByCode;

    static {
        MedLinkMedtronicCommandType.RFPowerOn.maxAllowedTime = 17000;
        MedLinkMedtronicCommandType.RFPowerOn.allowedRetries = 0;
        MedLinkMedtronicCommandType.RFPowerOn.recordLength = 0;
        MedLinkMedtronicCommandType.RFPowerOn.minimalBufferSizeToStartReading = 1;

        mapByCode = new HashMap<>();

        for (MedLinkMedtronicCommandType medtronicCommandType : values()) {
            System.out.println(medtronicCommandType.toString());
            if(medtronicCommandType.command != null) {
                mapByCode.put(medtronicCommandType.command.code, medtronicCommandType);
            }
        }
    }

    public MedLinkCommandType command ;
    public byte commandCode = 0;
    public String commandDescription = "";
    public byte[] commandParameters = null;
    public int commandParametersCount = 0;
    public int maxRecords = 1;
    private Integer resourceId;
    public int command_type = 0;
    public int allowedRetries = 2;
    public int maxAllowedTime = 2000;
    public MinimedCommandParameterType parameterType;
    public int minimalBufferSizeToStartReading = 14;
    public int expectedLength = 0;
    //MinimedTargetType targetType;
    MedtronicDeviceType devices;
    private int recordLength = 64;


    MedLinkMedtronicCommandType() {
        // this is for "fake" commands needed by AAPS MedtronicUITask
    }
    MedLinkMedtronicCommandType(int code) {
        // this is for "fake" commands needed by AAPS MedtronicUITask
        this.command_type = (byte) code;
    }

    //    MedtronicCommandType(int code, String description, MedtronicDeviceType devices,
//            MinimedCommandParameterType parameterType) {
//        this(code, description, devices, parameterType, 64, 1, 0, 0, 0, 0);
//    }
//
//
//    MedtronicCommandType(int code, String description, MedtronicDeviceType devices,
//            MinimedCommandParameterType parameterType, int expectedLength) {
//        this(code, description, devices, parameterType, 64, 1, 0, 0, 0, expectedLength);
//    }
//
//
//    MedtronicCommandType(int code, String description, MedtronicDeviceType devices,
//            MinimedCommandParameterType parameterType, int recordLength, int maxRecords, int commandType) {
//        this(code, description, devices, parameterType, recordLength, maxRecords, 0, 0, commandType, 0);
//    }
//
//
//    MedtronicCommandType(int code, String description, MedtronicDeviceType devices,
//            MinimedCommandParameterType parameterType, int recordLength, int maxRecords, int commandType,
//            int expectedLength) {
//        this(code, description, devices, parameterType, recordLength, maxRecords, 0, 0, commandType,
//            expectedLength);
//    }
//
//
    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices,
                                MinimedCommandParameterType parameterType, byte[] cmd_params) {
        this(code, description, devices, parameterType, 0, 1, 0, 0, 11, 0);

        this.commandParameters = cmd_params;
        this.commandParametersCount = cmd_params.length;
    }


    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, //
                                MinimedCommandParameterType parameterType, MedLinkCommandType command) {

        this(code, description, devices, parameterType, 64, 1, 0, null, command);
    }


    // NEW
//    MedlinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices,
//                         MinimedCommandParameterType parameterType, int recordLength, int maxRecords, int commandType) {
//        this(code, description, devices, parameterType, recordLength, maxRecords, 0, null);
//    }


    // NEW
    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, //
                                MinimedCommandParameterType parameterType, int expectedLength, MedLinkCommandType command) {
        this(code, description, devices, parameterType, 64, 1, expectedLength, null, command);
    }


    // NEW
    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, //
                                MinimedCommandParameterType parameterType, int expectedLength,
                                int resourceId, MedLinkCommandType command) {
        this(code, description, devices, parameterType, 64, 1, expectedLength, resourceId, command);
    }

    MedLinkMedtronicCommandType(int code, String description,
                                MedtronicDeviceType devices, //
                                MinimedCommandParameterType parameterType, int recordLength, int max_recs, int expectedLength,
                                Integer resourceId) {
        this(code,description, devices, parameterType, recordLength, max_recs, expectedLength, resourceId, null);
    }

    // NEW
    MedLinkMedtronicCommandType(int code, String description,
                                MedtronicDeviceType devices, //
                                MinimedCommandParameterType parameterType, int recordLength, int max_recs, int expectedLength,
                                Integer resourceId, MedLinkCommandType command) {
        this.command = command;
        this.commandCode = (byte) code;
        this.commandDescription = description;
        this.devices = devices;
        this.recordLength = recordLength;
        this.maxRecords = max_recs;
        this.resourceId = resourceId;

        this.commandParametersCount = 0;
        this.allowedRetries = 2;
        this.parameterType = parameterType;
        this.expectedLength = expectedLength;

        if (this.parameterType == MinimedCommandParameterType.SubCommands) {
            this.minimalBufferSizeToStartReading = 200;
        }
    }


    @Deprecated MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, //
                                            MinimedCommandParameterType parameterType, int recordLength, int max_recs, int addy, //
                                            int addy_len, int cmd_type, int expectedLength) {
        this.commandCode = (byte) code;
        this.commandDescription = description;
        //this.targetType = targetType;
        this.devices = devices;
        this.recordLength = recordLength;
        this.maxRecords = max_recs;

        this.command_type = cmd_type;
        this.commandParametersCount = 0;
        this.allowedRetries = 2;
        this.parameterType = parameterType;
        this.expectedLength = expectedLength;

        if (this.parameterType == MinimedCommandParameterType.SubCommands) {
            this.minimalBufferSizeToStartReading = 200;
        }

    }

    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, MinimedCommandParameterType parameterType, int recordLength) {
        this(code,description,devices,parameterType,recordLength,null);
    }

    MedLinkMedtronicCommandType(int code, String description, MedtronicDeviceType devices, MinimedCommandParameterType parameterType) {
        this(code,description,devices,parameterType,0,null);
    }

    private static HashMap<MedtronicDeviceType, String> getDeviceTypesArray(MedtronicDeviceType... types) {
        HashMap<MedtronicDeviceType, String> hashMap = new HashMap<MedtronicDeviceType, String>();

        for (MedtronicDeviceType type : types) {
            hashMap.put(type, null);
        }

        return hashMap;
    }


    private static byte[] getByteArray(int... data) {
        byte[] array = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            array[i] = (byte) data[i];
        }

        return array;
    }


    private static int[] getIntArray(int... data) {
        return data;
    }


    public MedLinkMedtronicCommandType getByCode(byte code) {
        if (mapByCode.containsKey(code)) {
            return mapByCode.get(code);
        } else {
            return MedLinkMedtronicCommandType.InvalidCommand;
        }
    }


    public static MessageBody constructMessageBody(MedLinkMedtronicCommandType messageType, byte[] bodyData) {
        switch (messageType) {
            case Bolus:
                return new PumpAckMessageBody(bodyData);
            default:
                return new UnknownMessageBody(bodyData);
        }
    }

    //TODO check if is necessary to adapt to the new firmware pumps
    public static MedLinkMedtronicCommandType getSettings(MedLinkMedtronicDeviceType medtronicPumpModel) {
        if (MedLinkMedtronicDeviceType.isSameDevice(medtronicPumpModel, MedLinkMedtronicDeviceType.MedLinkMedtronic_512_712))
            return MedLinkMedtronicCommandType.Settings_512;
        else
            return MedLinkMedtronicCommandType.Settings;
    }


    /**
     * Get Full Command Description
     *
     * @return command description
     */
    public String getFullCommandDescription() {
        return "Command [name=" + this.name() + ", id=" + this.commandCode + ",description=" + this.commandDescription
                + "] ";
    }


    public boolean canReturnData() {
        System.out.println("CanReturnData: ]id=" + this.name() + "max=" + this.maxRecords + "recLen=" + recordLength);
        return (this.maxRecords * this.recordLength) > 0;
    }


    public int getRecordLength() {
        return recordLength;
    }


    public int getMaxRecords() {
        return maxRecords;
    }


    public byte getCommandCode() {
        return commandCode;
    }

    public MedLinkCommandType getCommand() {
        return command;
    }

    public int getCommandParametersCount() {
        if (this.commandParameters == null) {
            return 0;
        } else {
            return this.commandParameters.length;
        }
    }


    public byte[] getCommandParameters() {
        return commandParameters;
    }


    public boolean hasCommandParameters() {
        return (getCommandParametersCount() > 0);
    }


    public String toString() {
        return name();
    }


    public String getCommandDescription() {
        return this.commandDescription;
    }


    public Integer getResourceId() {
        return resourceId;
    }

    public enum MinimedCommandParameterType {
        NoParameters, //
        FixedParameters, //
        SubCommands //
    }

}
