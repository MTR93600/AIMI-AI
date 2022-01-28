package info.nightscout.androidaps.plugins.pump.common.data;

import java.time.ZonedDateTime;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.EnliteInMemoryGlucoseValue;
import info.nightscout.androidaps.data.InMemoryGlucoseValue;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by Dirceu on 21/01/21.
 */
public abstract class MedLinkPumpStatus extends  MedLinkPartialBolus{

    public Integer sensorAge;
    public Double isig;
    public Double calibrationFactor;
    public Double yesterdayTotalUnits;
    public double deviceBatteryVoltage;
    public int deviceBatteryRemaining;
    public ZonedDateTime nextCalibration;
    public boolean bgAlarmOn;

    @Override public String toString() {
        return "MedLinkPumpStatus{" +
                "sensorAge=" + sensorAge +
                ", isig=" + isig +
                ", calibrationFactor=" + calibrationFactor +
                ", deviceBatteryVoltage=" + deviceBatteryVoltage +
                ", deviceBatteryRemaining=" + deviceBatteryRemaining +
                ", nextCalibration=" + nextCalibration +
                ", bgAlarmOn=" + bgAlarmOn +
                ", lastReadingStatus=" + lastReadingStatus +
                ", currentReadingStatus=" + currentReadingStatus +
                ", lastBGTimestamp=" + lastBGTimestamp +
                ", latestBG=" + latestBG +
                ", bolusDeliveredAmount=" + bolusDeliveredAmount +
                ", lastConnection=" + getLastConnection() +
                ", lastBolusTime=" + getLastBolusTime() +
                ", lastBolusAmount=" + getLastBolusAmount() +
                '}';
    }

    public enum BGReadingStatus{
        SUCCESS,
        FAILED
    }

    public EnliteInMemoryGlucoseValue sensorDataReading;
    public InMemoryGlucoseValue bgReading;
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
        if(getLastBolusAmount() !=null) {
            result.insulin = getLastBolusAmount();
        }
        if(getLastBolusTime() !=null) {
            result.setDeliverAtTheLatest( getLastBolusTime().getTime());
        }
        return result;
    }

//    public PumpStatusType getPumpStatusType(){
//        return pumpStatusType;
//    }
}

