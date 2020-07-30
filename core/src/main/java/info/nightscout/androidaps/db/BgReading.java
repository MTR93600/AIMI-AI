package info.nightscout.androidaps.db;

import androidx.annotation.NonNull;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

@DatabaseTable(tableName = "BgReadings")
public class BgReading implements DataPointWithLabelInterface {
    @Inject public AAPSLogger aapsLogger;
    @Inject public DefaultValueHelper defaultValueHelper;
    @Inject public ProfileFunction profileFunction;
    @Inject public ResourceHelper resourceHelper;
    @Inject public DateUtil dateUtil;

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public double value;
    @DatabaseField
    public String direction;
    @DatabaseField
    public double raw;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id

    public boolean isCOBPrediction = false; // true when drawing predictions as bg points (COB)
    public boolean isaCOBPrediction = false; // true when drawing predictions as bg points (aCOB)
    public boolean isIOBPrediction = false; // true when drawing predictions as bg points (IOB)
    public boolean isUAMPrediction = false; // true when drawing predictions as bg points (UAM)
    public boolean isZTPrediction = false; // true when drawing predictions as bg points (ZT)

    public BgReading() {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
    }

    public BgReading(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public BgReading(HasAndroidInjector injector, NSSgv sgv) {
        injector.androidInjector().inject(this);
        date = sgv.getMills();
        value = sgv.getMgdl();
        raw = sgv.getFiltered() != null ? sgv.getFiltered() : value;
        direction = sgv.getDirection();
        _id = sgv.getId();
    }

    public Double valueToUnits(String units) {
        if (units.equals(Constants.MGDL))
            return value;
        else
            return value * Constants.MGDL_TO_MMOLL;
    }

    public String valueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(value);
        else return DecimalFormatter.to1Decimal(value * Constants.MGDL_TO_MMOLL);
    }

    public String directionToSymbol(DatabaseHelperInterface databaseHelper) {
        String symbol = "";
        if (direction == null)
            direction = calculateDirection(databaseHelper);

        if (direction.compareTo("DoubleDown") == 0) {
            symbol = "\u21ca";
        } else if (direction.compareTo("SingleDown") == 0) {
            symbol = "\u2193";
        } else if (direction.compareTo("FortyFiveDown") == 0) {
            symbol = "\u2198";
        } else if (direction.compareTo("Flat") == 0) {
            symbol = "\u2192";
        } else if (direction.compareTo("FortyFiveUp") == 0) {
            symbol = "\u2197";
        } else if (direction.compareTo("SingleUp") == 0) {
            symbol = "\u2191";
        } else if (direction.compareTo("DoubleUp") == 0) {
            symbol = "\u21c8";
        } else if (isSlopeNameInvalid(direction)) {
            symbol = "??";
        }
        return symbol;
    }

    private static boolean isSlopeNameInvalid(String direction) {
        return direction.compareTo("NOT_COMPUTABLE") == 0 ||
                direction.compareTo("NOT COMPUTABLE") == 0 ||
                direction.compareTo("OUT_OF_RANGE") == 0 ||
                direction.compareTo("OUT OF RANGE") == 0 ||
                direction.compareTo("NONE") == 0 ||
                direction.compareTo("NotComputable") == 0;
    }


    @NonNull @Override
    public String toString() {
        return "BgReading{" +
                "date=" + date +
                ", date=" + dateUtil.dateAndTimeString(date) +
                ", value=" + value +
                ", direction=" + direction +
                ", raw=" + raw +
                '}';
    }

    public boolean isDataChanging(BgReading other) {
        if (date != other.date) {
            aapsLogger.debug(LTag.GLUCOSE, "Comparing different");
            return false;
        }
        if (value != other.value)
            return true;
        return false;
    }

    public boolean isEqual(BgReading other) {
        if (date != other.date) {
            aapsLogger.debug(LTag.GLUCOSE, "Comparing different");
            return false;
        }
        if (value != other.value)
            return false;
        if (raw != other.raw)
            return false;
        if (!Objects.equals(direction, other.direction))
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(BgReading other) {
        if (date != other.date) {
            aapsLogger.error(LTag.GLUCOSE, "Copying different");
            return;
        }
        value = other.value;
        raw = other.raw;
        direction = other.direction;
        _id = other._id;
    }

    public BgReading date(long date) {
        this.date = date;
        return this;
    }

    public BgReading date(Date date) {
        this.date = date.getTime();
        return this;
    }

    public BgReading value(double value) {
        this.value = value;
        return this;
    }

    // ------------------ DataPointWithLabelInterface ------------------
    @Override
    public double getX() {
        return date;
    }

    @Override
    public double getY() {
        return valueToUnits(profileFunction.getUnits());
    }

    @Override
    public void setY(double y) {

    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        if (isPrediction())
            return PointsWithLabelGraphSeries.Shape.PREDICTION;
        else
            return PointsWithLabelGraphSeries.Shape.BG;
    }

    @Override
    public float getSize() {
        return 1;
    }

    @Override
    public int getColor() {
        String units = profileFunction.getUnits();
        Double lowLine = defaultValueHelper.determineLowLine();
        Double highLine = defaultValueHelper.determineHighLine();
        int color = resourceHelper.gc(R.color.inrange);
        if (isPrediction())
            return getPredectionColor();
        else if (valueToUnits(units) < lowLine)
            color = resourceHelper.gc(R.color.low);
        else if (valueToUnits(units) > highLine)
            color = resourceHelper.gc(R.color.high);
        return color;
    }

    public int getPredectionColor() {
        if (isIOBPrediction)
            return resourceHelper.gc(R.color.iob);
        if (isCOBPrediction)
            return resourceHelper.gc(R.color.cob);
        if (isaCOBPrediction)
            return 0x80FFFFFF & resourceHelper.gc(R.color.cob);
        if (isUAMPrediction)
            return resourceHelper.gc(R.color.uam);
        if (isZTPrediction)
            return resourceHelper.gc(R.color.zt);
        return R.color.white;
    }

    private boolean isPrediction() {
        return isaCOBPrediction || isCOBPrediction || isIOBPrediction || isUAMPrediction || isZTPrediction;
    }


    // Copied from xDrip+
    String calculateDirection(DatabaseHelperInterface databasehelper) {
        // Rework to get bgreaings from internal DB and calculate on that base

        List<BgReading> bgReadingsList = databasehelper.getAllBgreadingsDataFromTime(this.date - T.mins(10).msecs(), false);
        if (bgReadingsList == null || bgReadingsList.size() < 2)
            return "NONE";
        BgReading current = bgReadingsList.get(1);
        BgReading previous = bgReadingsList.get(0);

        if (bgReadingsList.get(1).date < bgReadingsList.get(0).date) {
            current = bgReadingsList.get(0);
            previous = bgReadingsList.get(1);
        }

        double slope;

        // Avoid division by 0
        if (current.date == previous.date)
            slope = 0;
        else
            slope = (previous.value - current.value) / (previous.date - current.date);

        aapsLogger.error(LTag.GLUCOSE, "Slope is :" + slope + " delta " + (previous.value - current.value) + " date difference " + (current.date - previous.date));

        double slope_by_minute = slope * 60000;
        String arrow = "NONE";

        if (slope_by_minute <= (-3.5)) {
            arrow = "DoubleDown";
        } else if (slope_by_minute <= (-2)) {
            arrow = "SingleDown";
        } else if (slope_by_minute <= (-1)) {
            arrow = "FortyFiveDown";
        } else if (slope_by_minute <= (1)) {
            arrow = "Flat";
        } else if (slope_by_minute <= (2)) {
            arrow = "FortyFiveUp";
        } else if (slope_by_minute <= (3.5)) {
            arrow = "SingleUp";
        } else if (slope_by_minute <= (40)) {
            arrow = "DoubleUp";
        }
        aapsLogger.error(LTag.GLUCOSE, "Direction set to: " + arrow);
        return arrow;
    }
}
