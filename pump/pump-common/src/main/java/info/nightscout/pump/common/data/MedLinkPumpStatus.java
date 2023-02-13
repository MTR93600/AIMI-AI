package info.nightscout.pump.common.data;

import androidx.annotation.NonNull;

import java.time.ZonedDateTime;

import info.nightscout.androidaps.interfaces.BgSync;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.interfaces.pump.EnliteInMemoryGlucoseValue;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.pump.common.sync.PumpDbEntryTBR;

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
    public double currentBasal;
    public int tempBasalRemainMin;
    public PumpDbEntryTBR runningTBR;
    private boolean batteryChanged;
    private long lastBatteryChanged;

    public boolean isBatteryChanged() {
        return batteryChanged;
    }

    public void setBatteryChanged(boolean batteryChanged) {
        this.lastBatteryChanged = System.currentTimeMillis();
        this.batteryChanged = batteryChanged;
    }

    public long getLastBatteryChanged() {
        return lastBatteryChanged;
    }


    @NonNull @Override public String toString() {
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

    public BgSync.BgHistory.BgValue sensorDataReading;
    public EnliteInMemoryGlucoseValue bgReading;
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

