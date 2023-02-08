package info.nightscout.androidaps.plugins.pump.medtronic.defs;

/**
 * Created by dirceu
 *
 * used by medlink
 */

public enum MedLinkMedtronicStatusRefreshType {

    PumpHistory(5, null), //
    Configuration(0, null), //
    RemainingInsulin(-1, MedLinkMedtronicCommandType.GetRemainingInsulin), //
    BatteryStatus(55, MedLinkMedtronicCommandType.GetBatteryStatus), //
    PumpTime(60, MedLinkMedtronicCommandType.GetRealTimeClock) //
    ;

    private int refreshTime;
    private MedLinkMedtronicCommandType commandType;


    MedLinkMedtronicStatusRefreshType(int refreshTime, MedLinkMedtronicCommandType commandType) {
        this.refreshTime = refreshTime;
        this.commandType = commandType;
    }


    public int getRefreshTime() {
        return refreshTime;
    }


    public MedLinkMedtronicCommandType getCommandType(MedLinkMedtronicDeviceType medtronicDeviceType) {
        if (this == Configuration) {
            return MedLinkMedtronicCommandType.getSettings(medtronicDeviceType);
        } else
            return commandType;
    }
}
