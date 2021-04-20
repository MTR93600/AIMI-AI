package info.nightscout.androidaps.db;

import info.nightscout.androidaps.Constants;

/**
 * Created by Dirceu on 11/04/21.
 */
public abstract class BgConversion {

    public double previousBG;
    public long previousDate;

    public Double valueToUnits(Double bgValue, String units) {
        if (units.equals(Constants.MGDL))
            return bgValue;
        else
            return bgValue * Constants.MGDL_TO_MMOLL;
    }

    protected double calculateSlope(long currentDate, long previousDate, double currentValue, double previousValue) {
        double slope = 0d;
        if (currentDate == previousDate)
            slope = 0;
        else
            slope = (previousValue - currentValue) / (previousDate - currentDate);
        return slope;
    }
}
