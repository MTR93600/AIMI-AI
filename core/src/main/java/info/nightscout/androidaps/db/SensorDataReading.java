package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSens;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by Dirceu on 10/04/21.
 */
@DatabaseTable(tableName = "SensorDataReadings")
public class SensorDataReading extends BgConversion implements DataPointWithLabelInterface {

    public BgReading bgReading;
    @Inject public AAPSLogger aapsLogger;
    @Inject public DefaultValueHelper defaultValueHelper;
    @Inject public ProfileFunction profileFunction;
    @Inject public ResourceHelper resourceHelper;
    @Inject public DateUtil dateUtil;

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public double bgValue;

    @DatabaseField
    public double calibrationFactor;
    @DatabaseField
    public double isig;
    @DatabaseField
    public String direction;
    @DatabaseField
    public int sensorUptime;
    @DatabaseField
    public double deltaSinceLastBG;


    @DatabaseField
    public String _id = null; // NS _id


    public SensorDataReading() {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
    }

    public SensorDataReading(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public SensorDataReading(HasAndroidInjector injector, NSSens ssens) {
        injector.androidInjector().inject(this);
        date = ssens.getMills();
        sensorUptime = ssens.getSensorUptime();
        bgValue = ssens.getBG();
        isig = ssens.getIsig();
        direction = ssens.getDirection();
        calibrationFactor = ssens.getCalibrationFactor();
        deltaSinceLastBG = ssens.getDeltaSinceLastBG();
        _id = ssens.getId();
    }

    public SensorDataReading(HasAndroidInjector injector, BgReading bgReading, Double isig,
                             Double calibrationFactor) {
        injector.androidInjector().inject(this);
        date = bgReading.date;
        bgValue = bgReading.value;
        this.isig = isig;
        this.direction = bgReading.direction;
        this.calibrationFactor = calibrationFactor;
        this.deltaSinceLastBG = calculateSlope(date, bgReading.previousDate, bgValue,
                bgReading.previousBG);
        this.bgReading = bgReading;
    }

    // ------------------ DataPointWithLabelInterface ------------------
    @Override
    public double getX() {
        return date;
    }

    @Override
    public double getY() {
        return valueToUnits(bgValue, profileFunction.getUnits());
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
//        if (isPrediction())
//            return PointsWithLabelGraphSeries.Shape.PREDICTION;
//        else
        return PointsWithLabelGraphSeries.Shape.BG;
    }

    @Override
    public float getSize() {
        return 1;
    }

    @Override
    public int getColor() {
//        String units = profileFunction.getUnits();
        Double lowLine = defaultValueHelper.determineLowLine();
        Double highLine = defaultValueHelper.determineHighLine();
        int color = resourceHelper.gc(R.color.inrange);
        if (isig < lowLine)
            color = resourceHelper.gc(R.color.low);
        else if (isig > highLine)
            color = resourceHelper.gc(R.color.high);
        return color;
    }


    public boolean isEqual(SensorDataReading other) {
        if (date != other.date) {
            aapsLogger.debug(LTag.GLUCOSE, "Comparing different");
            return false;
        }
        if (bgValue != other.bgValue)
            return false;
        if (isig != other.isig)
            return false;
        if (calibrationFactor != other.calibrationFactor)
            return false;
        if (deltaSinceLastBG != other.deltaSinceLastBG)
            return false;
        if (sensorUptime != other.sensorUptime)
            return false;
        if (!Objects.equals(direction, other.direction))
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(SensorDataReading other) {
        if (date != other.date) {
            aapsLogger.error(LTag.GLUCOSE, "Copying different");
            return;
        }
        calibrationFactor = other.calibrationFactor;
        sensorUptime = other.sensorUptime;
        deltaSinceLastBG = other.deltaSinceLastBG;
        bgValue = other.bgValue;
        isig = other.isig;
        direction = other.direction;
        _id = other._id;
    }

    public BgReading getBgReading() {
        return bgReading;
    }

}

