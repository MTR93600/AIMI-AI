package info.nightscout.androidaps.db;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

@DatabaseTable(tableName = "ProfileSwitches")
public class ProfileSwitch implements Interval, DataPointWithLabelInterface {

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id

    @DatabaseField
    public boolean isCPP = false; // CPP NS="CircadianPercentageProfile"
    @DatabaseField
    public int timeshift = 0; // CPP NS="timeshift"
    @DatabaseField
    public int percentage = 100; // CPP NS="percentage"

    @DatabaseField
    public String profileName = null;

    @DatabaseField
    public String profileJson = null;

    @DatabaseField
    public String profilePlugin = null; // NSProfilePlugin.class.getName();

    @DatabaseField
    public int durationInMinutes = 0;

    private Profile profile = null;

    HasAndroidInjector injector;
    @Inject public TreatmentsInterface treatmentsPlugin;
    @Inject public AAPSLogger aapsLogger;
    @Inject public RxBusWrapper rxBus;
    @Inject public ResourceHelper resourceHelper;
    @Inject public DateUtil dateUtil;

    public ProfileSwitch() {
        this.injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this);
    }

    public ProfileSwitch(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
        this.injector = injector;
    }

    public ProfileSwitch date(long date) {
        this.date = date;
        return this;
    }

    public ProfileSwitch profileName(String profileName) {
        this.profileName = profileName;
        return this;
    }

    public ProfileSwitch profile(Profile profile) {
        this.profile = profile;
        return this;
    }

    public ProfileSwitch source(int source) {
        this.source = source;
        return this;
    }

    public ProfileSwitch duration(int duration) {
        this.durationInMinutes = duration;
        return this;
    }

    @Nullable
    public Profile getProfileObject() {
        if (profile == null)
            try {
                profile = new Profile(injector, new JSONObject(profileJson), percentage, timeshift);
            } catch (Exception e) {
                aapsLogger.error("Unhandled exception", e);
                aapsLogger.error("Unhandled exception: " + profileJson);
            }
        return profile;
    }

    /**
     * Note: the name returned here is used as the PS name when uploading to NS. When such a PS is retrieved
     * again from NS, the added parts must be removed again, see
     * {@link info.nightscout.androidaps.utils.PercentageSplitter#pureName}
     */
    public String getCustomizedName() {
        String name = profileName;
        if (Constants.LOCAL_PROFILE.equals(name)) {
            name = DecimalFormatter.to2Decimal(getProfileObject().percentageBasalSum()) + "U ";
        }
        if (isCPP) {
            name += "(" + percentage + "%";
            if (timeshift != 0)
                name += "," + timeshift + "h";
            name += ")";
        }
        return name;
    }

    public boolean isEqual(ProfileSwitch other) {
        if (date != other.date) {
            return false;
        }
        if (durationInMinutes != other.durationInMinutes)
            return false;
        if (percentage != other.percentage)
            return false;
        if (timeshift != other.timeshift)
            return false;
        if (isCPP != other.isCPP)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        if (!Objects.equals(profilePlugin, other.profilePlugin))
            return false;
        if (!Objects.equals(profileJson, other.profileJson))
            return false;
        if (!Objects.equals(profileName, other.profileName))
            return false;
        return true;
    }

    public void copyFrom(ProfileSwitch t) {
        date = t.date;
        _id = t._id;
        durationInMinutes = t.durationInMinutes;
        percentage = t.percentage;
        timeshift = t.timeshift;
        isCPP = t.isCPP;
        profilePlugin = t.profilePlugin;
        profileJson = t.profileJson;
        profileName = t.profileName;
    }

    // -------- Interval interface ---------

    private Long cuttedEnd = null;

    public long durationInMsec() {
        return durationInMinutes * 60 * 1000L;
    }

    public long start() {
        return date;
    }

    // planned end time at time of creation
    public long originalEnd() {
        return date + durationInMinutes * 60 * 1000L;
    }

    // end time after cut
    public long end() {
        if (cuttedEnd != null)
            return cuttedEnd;
        return originalEnd();
    }

    public void cutEndTo(long end) {
        cuttedEnd = end;
    }

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
        return durationInMinutes == 0;
    }

    @Override
    public boolean isValid() {
        boolean isValid = getProfileObject() != null && getProfileObject().isValid(dateUtil.dateAndTimeString(date));
        ProfileSwitch active = treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now());
        long activeProfileSwitchDate = active != null ? active.date : -1L;
        if (!isValid && date == activeProfileSwitchDate)
            createNotificationInvalidProfile(dateUtil.dateAndTimeString(date));
        return isValid;
    }

    private void createNotificationInvalidProfile(String detail) {
        Notification notification = new Notification(Notification.ZERO_VALUE_IN_PROFILE, resourceHelper.gs(R.string.zerovalueinprofile, detail), Notification.LOW, 5);
        rxBus.send(new EventNewNotification(notification));
    }

    public boolean isEvent5minBack(List<ProfileSwitch> list, long time, boolean zeroDurationOnly) {
        for (int i = 0; i < list.size(); i++) {
            ProfileSwitch event = list.get(i);
            if (event.date <= time && event.date > (time - T.mins(5).msecs())) {
                if (zeroDurationOnly) {
                    if (event.durationInMinutes == 0) {
                        aapsLogger.debug(LTag.DATABASE, "Found ProfileSwitch event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString());
                        return true;
                    }
                } else {
                    aapsLogger.debug(LTag.DATABASE, "Found ProfileSwitch event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString());
                    return true;
                }
            }
        }
        return false;
    }

    // -------- Interval interface end ---------

    //  ----------------- DataPointInterface --------------------
    @Override
    public double getX() {
        return date;
    }

    // default when no sgv around available
    private double yValue = 0;

    @Override
    public double getY() {
        return yValue;
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    @Override
    public String getLabel() {
        return getCustomizedName();
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.PROFILE;
    }

    @Override
    public float getSize() {
        return 10;
    }

    @Override
    public int getColor() {
        return Color.CYAN;
    }

    @NonNull
    public String toString() {
        return "ProfileSwitch{" +
                "date=" + date +
                "date=" + dateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", duration=" + durationInMinutes +
                ", profileName=" + profileName +
                ", percentage=" + percentage +
                ", timeshift=" + timeshift +
                '}';
    }

}
