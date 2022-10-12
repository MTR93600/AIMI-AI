package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.shared.logging.AAPSLogger;

/**
 * Created by Dirceu on 18/01/21.
 */
public class MedLinkBasalProfileEntry extends  BasalProfileEntry {

    private String time;
    public MedLinkBasalProfileEntry(double basalAmount, int hour, int minute) {
        super(basalAmount, hour, minute);
        this.setRate(basalAmount);
        time = ""+hour+":"+minute;
    }

    public String getTime() {
        return time;
    }
}