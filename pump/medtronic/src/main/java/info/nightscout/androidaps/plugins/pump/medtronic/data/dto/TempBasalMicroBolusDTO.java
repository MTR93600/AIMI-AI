package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import info.nightscout.rx.logging.AAPSLogger;



/**
 * used by medlink
 */
public class TempBasalMicroBolusDTO extends TempBasalPair {

    @Expose
    protected int endTimeInSeconds = 0;

    @Expose
    protected int sartTimeInSeconds = 0;

    public TempBasalMicroBolusDTO(byte rateByte, int startTimeByte, boolean isPercent) {
        super(rateByte, startTimeByte, isPercent);
    }

    public TempBasalMicroBolusDTO(AAPSLogger aapsLogger, byte[] response) {
        super(aapsLogger, response);
    }

    public TempBasalMicroBolusDTO(double insulinRate, boolean isPercent, int durationMinutes) {
        super(insulinRate, isPercent, durationMinutes);
    }

    public TempBasalMicroBolusDTO(double profileDosage, boolean isPercent, int durationMinutes,
                                  int startTimeInSeconds, int endTimeInSeconds) {
        super(profileDosage, isPercent, durationMinutes);
        this.sartTimeInSeconds = startTimeInSeconds;
        this.endTimeInSeconds = endTimeInSeconds;
    }

    public int getEndTimeInSeconds() {
        return endTimeInSeconds;
    }

    public void setEndTimeInSeconds(int endTimeInSeconds) {
        this.endTimeInSeconds = endTimeInSeconds;
    }

    public int getSartTimeInSeconds() {
        return sartTimeInSeconds;
    }

    public void setSartTimeInSeconds(int sartTimeInSeconds) {
        this.sartTimeInSeconds = sartTimeInSeconds;
    }
}
