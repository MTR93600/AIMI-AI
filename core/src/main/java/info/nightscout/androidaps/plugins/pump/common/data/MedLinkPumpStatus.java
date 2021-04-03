package info.nightscout.androidaps.plugins.pump.common.data;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by Dirceu on 21/01/21.
 */
public abstract class MedLinkPumpStatus extends PumpStatus {

    public Integer sensorAge;

    public enum BGReadingStatus{
        SUCCESS,
        FAILED
    }

    public BgReading reading;
    public BGReadingStatus lastReadingStatus = BGReadingStatus.FAILED;
    public BGReadingStatus currentReadingStatus = BGReadingStatus.FAILED;

    public MedLinkPumpStatus(PumpType pumpType) {
        super(pumpType);
    }

    public long getLastBGTimestamp() {
        return lastBGTimestamp;
    }

    public long lastBGTimestamp;
    public double latestBG;

    public DetailedBolusInfo getLastBolusInfo(){
        DetailedBolusInfo result = new DetailedBolusInfo();
        result.insulin = lastBolusAmount;
        result.deliverAt = lastBolusTime.getTime();
        return result;
    }
}

