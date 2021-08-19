package info.nightscout.androidaps.plugins.pump.common.data;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.SensorDataReading;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by Dirceu on 21/01/21.
 */
public abstract class MedLinkPumpStatus extends PumpStatus {

    public Integer sensorAge;
    public Double isig;
    public Double calibrationFactor;
    public Double yesterdayTotalUnits;
    public double deviceBatteryVoltage;
    public int deviceBatteryRemaining;
    public ZonedDateTime nextCalibration;
    public boolean bgAlarmOn;

    public enum BGReadingStatus{
        SUCCESS,
        FAILED
    }

    public SensorDataReading sensorDataReading;
    public BgReading bgReading;
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

    public PumpStatusType getPumpStatusType(){
        return pumpStatusType;
    }
}

