package info.nightscout.androidaps.db;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.Translator;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

@DatabaseTable(tableName = "CareportalEvents")
public class CareportalEvent implements DataPointWithLabelInterface, Interval {

    @Inject ProfileFunction profileFunction;
    @Inject ResourceHelper resourceHelper;
    @Inject AAPSLogger aapsLogger;
    @Inject Translator translator;
    @Inject DateUtil dateUtil;

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id;

    @DatabaseField
    public String eventType;
    @DatabaseField
    public String json;

    public static final String CARBCORRECTION = "Carb Correction";
    public static final String BOLUSWIZARD = "Bolus Wizard";
    public static final String CORRECTIONBOLUS = "Correction Bolus";
    public static final String MEALBOLUS = "Meal Bolus";
    public static final String COMBOBOLUS = "Combo Bolus";
    public static final String TEMPBASAL = "Temp Basal";
    public static final String TEMPORARYTARGET = "Temporary Target";
    public static final String PROFILESWITCH = "Profile Switch";
    public static final String SITECHANGE = "Site Change";
    public static final String INSULINCHANGE = "Insulin Change";
    public static final String SENSORCHANGE = "Sensor Change";
    public static final String PUMPBATTERYCHANGE = "Pump Battery Change";
    public static final String BGCHECK = "BG Check";
    public static final String ANNOUNCEMENT = "Announcement";
    public static final String NOTE = "Note";
    public static final String QUESTION = "Question";
    public static final String EXERCISE = "Exercise";
    public static final String OPENAPSOFFLINE = "OpenAPS Offline";
    public static final String NONE = "<none>";

    public static final String MBG = "Mbg"; // comming from entries

    @Deprecated
    public CareportalEvent() {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
    }

    public CareportalEvent(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public CareportalEvent(NSMbg mbg) {
        this();
        date = mbg.date;
        eventType = MBG;
        json = mbg.json;
    }

    public long getMillisecondsFromStart() {
        return System.currentTimeMillis() - date;
    }

    private double getHoursFromStart() {
        return (System.currentTimeMillis() - date) / (60 * 60 * 1000.0);
    }

    public String age(boolean useShortText, ResourceHelper resourceHelper) {
        Map<TimeUnit, Long> diff = DateUtil.computeDiff(date, System.currentTimeMillis());

        String days = " " + resourceHelper.gs(R.string.days) + " ";
        String hours = " " + resourceHelper.gs(R.string.hours) + " ";

        if (useShortText) {
            days = resourceHelper.gs(R.string.shortday);
            hours = resourceHelper.gs(R.string.shorthour);
        }

        return diff.get(TimeUnit.DAYS) + days + diff.get(TimeUnit.HOURS) + hours;
    }

    public boolean isOlderThan(double hours) {
        return getHoursFromStart() > hours;
    }

    @Override
    public String toString() {
        return "CareportalEvent{" +
                "date= " + date +
                ", date= " + dateUtil.dateAndTimeString(date) +
                ", isValid= " + isValid +
                ", _id= " + _id +
                ", eventType= " + eventType +
                ", json= " + json +
                "}";
    }

    public boolean isEvent5minBack(List<CareportalEvent> list, long time) {
        for (int i = 0; i < list.size(); i++) {
            CareportalEvent event = list.get(i);
            if (event.date <= time && event.date > (time - T.mins(5).msecs())) {
                aapsLogger.debug(LTag.DATABASE, "Found event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString());
                return true;
            }
        }
        return false;
    }

    // -------- DataPointWithLabelInterface -------

    @Override
    public double getX() {
        return date;
    }

    private double yValue = 0;

    @Override
    public double getY() {
        String units = profileFunction.getUnits();
        if (eventType.equals(MBG)) {
            double mbg = 0d;
            try {
                JSONObject object = new JSONObject(json);
                mbg = object.getDouble("mgdl");
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
            return Profile.fromMgdlToUnits(mbg, units);
        }

        double glucose = 0d;
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("glucose")) {
                glucose = object.getDouble("glucose");
                units = object.getString("units");
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        if (glucose != 0d) {
            double mmol = 0d;
            double mgdl = 0;
            if (units.equals(Constants.MGDL)) {
                mgdl = glucose;
                mmol = glucose * Constants.MGDL_TO_MMOLL;
            }
            if (units.equals(Constants.MMOL)) {
                mmol = glucose;
                mgdl = glucose * Constants.MMOLL_TO_MGDL;
            }
            return Profile.toUnits(mgdl, mmol, units);
        }

        return yValue;
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    @Override
    public String getLabel() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("notes"))
                return StringUtils.abbreviate(object.getString("notes"), 40);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return translator.translate(eventType);
    }

    public String getNotes() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("notes"))
                return object.getString("notes");
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return "";
    }

    @Override
    public long getDuration() {
        return end() - start();
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        switch (eventType) {
            case CareportalEvent.MBG:
                return PointsWithLabelGraphSeries.Shape.MBG;
            case CareportalEvent.BGCHECK:
                return PointsWithLabelGraphSeries.Shape.BGCHECK;
            case CareportalEvent.ANNOUNCEMENT:
                return PointsWithLabelGraphSeries.Shape.ANNOUNCEMENT;
            case CareportalEvent.OPENAPSOFFLINE:
                return PointsWithLabelGraphSeries.Shape.OPENAPSOFFLINE;
            case CareportalEvent.EXERCISE:
                return PointsWithLabelGraphSeries.Shape.EXERCISE;
        }
        if (getDuration() > 0)
            return PointsWithLabelGraphSeries.Shape.GENERALWITHDURATION;
        return PointsWithLabelGraphSeries.Shape.GENERAL;
    }

    @Override
    public float getSize() {
        boolean isTablet = resourceHelper.gb(R.bool.isTablet);
        return isTablet ? 12 : 10;
    }

    @Override
    public int getColor() {
        if (eventType.equals(ANNOUNCEMENT))
            return resourceHelper.gc(R.color.notificationAnnouncement);
        if (eventType.equals(MBG))
            return Color.RED;
        if (eventType.equals(BGCHECK))
            return Color.RED;
        if (eventType.equals(EXERCISE))
            return Color.BLUE;
        if (eventType.equals(OPENAPSOFFLINE))
            return Color.GRAY & 0x80FFFFFF;
        return Color.GRAY;
    }

    // Interval interface
    private Long cuttedEnd = null;

    @Override
    public long durationInMsec() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("duration"))
                return object.getInt("duration") * 60 * 1000L;
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0;
    }

    @Override
    public long start() {
        return date;
    }

    @Override
    public long originalEnd() {
        return date + durationInMsec();
    }

    @Override
    public long end() {
        if (cuttedEnd != null)
            return cuttedEnd;
        return originalEnd();
    }

    @Override
    public void cutEndTo(long end) {
        cuttedEnd = end;
    }

    @Override
    public boolean match(long time) {
        if (start() <= time && end() >= time)
            return true;
        return false;
    }

    public boolean before(long time) {
        if (end() < time)
            return true;
        return false;
    }

    public boolean after(long time) {
        if (start() > time)
            return true;
        return false;
    }

    @Override
    public boolean isInProgress() {
        return match(System.currentTimeMillis());
    }

    @Override
    public boolean isEndingEvent() {
        return durationInMsec() == 0;
    }

    @Override
    public boolean isValid() {
        return eventType.equals(OPENAPSOFFLINE);
    }

}
