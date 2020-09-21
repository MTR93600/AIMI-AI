package info.nightscout.androidaps.utils;

import android.content.Context;

import androidx.collection.LongSparseArray;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */

@Singleton
public class DateUtil {
    private final Context context;
    private final ResourceHelper resourceHelper;

    @Inject
    public DateUtil(Context context, ResourceHelper resourceHelper) {
        this.context = context;
        this.resourceHelper = resourceHelper;
    }

    /**
     * The date format in iso.
     */
    private static String FORMAT_DATE_ISO_OUT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /**
     * Takes in an ISO date string of the following format:
     * yyyy-mm-ddThh:mm:ss.ms+HoMo
     *
     * @param isoDateString the iso date string
     * @return the date
     */
    public static Date fromISODateString(String isoDateString) {

        DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
        DateTime dateTime = DateTime.parse(isoDateString, parser);
        return dateTime.toDate();
    }

    /**
     * Render date
     *
     * @param date   the date obj
     * @param format - if not specified, will use FORMAT_DATE_ISO
     * @param tz     - tz to set to, if not specified uses local timezone
     * @return the iso-formatted date string
     */
    public static String toISOString(Date date, String format, TimeZone tz) {
        if (format == null) format = FORMAT_DATE_ISO_OUT;
        if (tz == null) tz = TimeZone.getDefault();
        DateFormat f = new SimpleDateFormat(format, Locale.getDefault());
        f.setTimeZone(tz);
        return f.format(date);
    }

    public static String toISOString(Date date) {
        return toISOString(date, FORMAT_DATE_ISO_OUT, TimeZone.getTimeZone("UTC"));
    }

    public static String toISOString(long date) {
        return toISOString(new Date(date), FORMAT_DATE_ISO_OUT, TimeZone.getTimeZone("UTC"));
    }

    public static String toISOAsUTC(final long timestamp) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'0000Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(timestamp);
    }

    public static String toISONoZone(final long timestamp) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(timestamp);
    }

    public static Date toDate(Integer seconds) {
        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.MONTH, 0); // Set january to be sure we miss DST changing
        calendar.set(Calendar.HOUR_OF_DAY, seconds / 60 / 60);
        calendar.set(Calendar.MINUTE, (seconds / 60) % 60);
        calendar.set(Calendar.SECOND, 0);
        return calendar.getTime();
    }

    public static int toSeconds(String hh_colon_mm) {
        Pattern p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)");
        Matcher m = p.matcher(hh_colon_mm);
        int retval = 0;

        if (m.find()) {
            retval = SafeParse.stringToInt(m.group(1)) * 60 * 60 + SafeParse.stringToInt(m.group(2)) * 60;
            if ((m.group(3).equals(" a.m.") || m.group(3).equals(" AM") || m.group(3).equals("AM")) && m.group(1).equals("12"))
                retval -= 12 * 60 * 60;
            if ((m.group(3).equals(" p.m.") || m.group(3).equals(" PM") || m.group(3).equals("PM")) && !(m.group(1).equals("12")))
                retval += 12 * 60 * 60;
        }
        return retval;
    }

    public static long toTodayTime(String hh_colon_mm) {
        Pattern p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)");
        Matcher m = p.matcher(hh_colon_mm);
        long retval = 0;

        if (m.find()) {
            int hours = SafeParse.stringToInt(m.group(1));
            int minutes = SafeParse.stringToInt(m.group(2));
            if ((m.group(3).equals(" a.m.") || m.group(3).equals(" AM") || m.group(3).equals("AM")) && m.group(1).equals("12"))
                hours -= 12;
            if ((m.group(3).equals(" p.m.") || m.group(3).equals(" PM") || m.group(3).equals("PM")) && !(m.group(1).equals("12")))
                hours += 12;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hours);
            c.set(Calendar.MINUTE, minutes);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            retval = c.getTimeInMillis();
        }
        return retval;
    }

    public static String dateString(Date date) {
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        return df.format(date);
    }

    public static String dateString(long mills) {
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        return df.format(mills);
    }

    public String dateStringShort(long mills) {
        String format = "MM/dd";
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "dd/MM";
        }
        return new DateTime(mills).toString(DateTimeFormat.forPattern(format));
    }

    public String timeString(Date date) {
        String format = "hh:mma";
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm";
        }
        return new DateTime(date).toString(DateTimeFormat.forPattern(format));
    }

    public String timeString(long mills) {
        String format = "hh:mma";
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm";
        }
        return new DateTime(mills).toString(DateTimeFormat.forPattern(format));
    }

    public String timeStringWithSeconds(long mills) {
        String format = "hh:mm:ssa";
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm:ss";
        }
        return new DateTime(mills).toString(DateTimeFormat.forPattern(format));
    }

    public static String timeFullString(long mills) {
        return new DateTime(mills).toString(DateTimeFormat.fullTime());
    }

    public String dateAndTimeString(Date date) {
        return dateString(date) + " " + timeString(date);
    }

    public String dateAndTimeRangeString(long start, long end) {
        return dateAndTimeString(start) + " - " + timeString(end);
    }

    public String dateAndTimeString(long mills) {
        if (mills == 0) return "";
        return dateString(mills) + " " + timeString(mills);
    }

    public String dateAndTimeAndSecondsString(long mills) {
        if (mills == 0) return "";
        return dateString(mills) + " " + timeStringWithSeconds(mills);
    }

    public static String minAgo(ResourceHelper resourceHelper, long time) {
        int mins = (int) ((now() - time) / 1000 / 60);
        return resourceHelper.gs(R.string.minago, mins);
    }

    public static String minAgoShort(long time) {
        int mins = (int) ((time - now()) / 1000 / 60);
        return (mins > 0 ? "+" : "") + Integer.toString(mins);
    }

    public static String hourAgo(long time, ResourceHelper resourceHelper) {
        double hours = (now() - time) / 1000d / 60 / 60;
        return resourceHelper.gs(R.string.hoursago, hours);
    }

    private static LongSparseArray<String> timeStrings = new LongSparseArray<>();

    public String timeStringFromSeconds(int seconds) {
        String cached = timeStrings.get(seconds);
        if (cached != null)
            return cached;
        String t = timeString(toDate(seconds));
        timeStrings.put(seconds, t);
        return t;
    }


    public static String timeFrameString(long timeInMillis, ResourceHelper resourceHelper) {
        long remainingTimeMinutes = timeInMillis / (1000 * 60);
        long remainingTimeHours = remainingTimeMinutes / 60;
        remainingTimeMinutes = remainingTimeMinutes % 60;
        return "(" + ((remainingTimeHours > 0) ? (remainingTimeHours + resourceHelper.gs(R.string.shorthour) + " ") : "") + remainingTimeMinutes + "')";
    }

    public static String sinceString(long timestamp, ResourceHelper resourceHelper) {
        return timeFrameString(System.currentTimeMillis() - timestamp, resourceHelper);
    }

    public static String untilString(long timestamp, ResourceHelper resourceHelper) {
        return timeFrameString(timestamp - System.currentTimeMillis(), resourceHelper);
    }

    public long _now() {
        return System.currentTimeMillis();
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static long roundDateToSec(long date) {
        return date - date % 1000;
    }

    public static boolean isCloseToNow(long date) {
        long diff = Math.abs(date - now());
        return diff < T.mins(2).msecs();
    }

    public static boolean isOlderThan(long date, long minutes) {
        long diff = now() - date;
        return diff > T.mins(minutes).msecs();
    }

    public static GregorianCalendar gregorianCalendar() {
        return new GregorianCalendar();
    }

    public static long getTimeZoneOffsetMs() {
        return new GregorianCalendar().getTimeZone().getRawOffset();
    }

    public static int getTimeZoneOffsetMinutes(final long timestamp) {
        return TimeZone.getDefault().getOffset(timestamp) / 60000;
    }

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    public static Map<TimeUnit, Long> computeDiff(long date1, long date2) {
        long diffInMillies = date2 - date1;
        List<TimeUnit> units = new ArrayList<>(EnumSet.allOf(TimeUnit.class));
        Collections.reverse(units);
        Map<TimeUnit, Long> result = new LinkedHashMap<>();
        long milliesRest = diffInMillies;
        for (TimeUnit unit : units) {
            long diff = unit.convert(milliesRest, TimeUnit.MILLISECONDS);
            long diffInMilliesForUnit = unit.toMillis(diff);
            milliesRest = milliesRest - diffInMilliesForUnit;
            result.put(unit, diff);
        }
        return result;
    }

    public static String age(long milliseconds, boolean useShortText, ResourceHelper resourceHelper) {
        Map<TimeUnit, Long> diff = computeDiff(0L, milliseconds);

        String days = " " + resourceHelper.gs(R.string.days) + " ";
        String hours = " " + resourceHelper.gs(R.string.hours) + " ";
        String minutes = " " + resourceHelper.gs(R.string.unit_minutes) + " ";

        if (useShortText) {
            days = resourceHelper.gs(R.string.shortday);
            hours = resourceHelper.gs(R.string.shorthour);
            minutes = resourceHelper.gs(R.string.shortminute);
        }

        String result = "";
        if (diff.get(TimeUnit.DAYS) > 0) result += diff.get(TimeUnit.DAYS) + days;
        if (diff.get(TimeUnit.HOURS) > 0) result += diff.get(TimeUnit.HOURS) + hours;
        if (diff.get(TimeUnit.DAYS) == 0) result += diff.get(TimeUnit.MINUTES) + minutes;
        return result;
    }

    public static String niceTimeScalar(long t, ResourceHelper resourceHelper) {
        String unit = resourceHelper.gs(R.string.unit_second);
        t = t / 1000;
        if (t != 1) unit = resourceHelper.gs(R.string.unit_seconds);
        if (t > 59) {
            unit = resourceHelper.gs(R.string.unit_minute);
            t = t / 60;
            if (t != 1) unit = resourceHelper.gs(R.string.unit_minutes);
            if (t > 59) {
                unit = resourceHelper.gs(R.string.unit_hour);
                t = t / 60;
                if (t != 1) unit = resourceHelper.gs(R.string.unit_hours);
                if (t > 24) {
                    unit = resourceHelper.gs(R.string.unit_day);
                    t = t / 24;
                    if (t != 1) unit = resourceHelper.gs(R.string.unit_days);
                    if (t > 28) {
                        unit = resourceHelper.gs(R.string.unit_week);
                        t = t / 7;
                        if (t != 1) unit = resourceHelper.gs(R.string.unit_weeks);
                    }
                }
            }
        }
        //if (t != 1) unit = unit + "s"; //implemented plurality in every step, because in other languages plurality of time is not every time adding the same character
        return qs((double) t, 0) + " " + unit;
    }

    // singletons to avoid repeated allocation
    private static DecimalFormatSymbols dfs;
    private static DecimalFormat df;

    public static String qs(double x, int digits) {

        if (digits == -1) {
            digits = 0;
            if (((int) x != x)) {
                digits++;
                if ((((int) x * 10) / 10 != x)) {
                    digits++;
                    if ((((int) x * 100) / 100 != x)) digits++;
                }
            }
        }

        if (dfs == null) {
            final DecimalFormatSymbols local_dfs = new DecimalFormatSymbols();
            local_dfs.setDecimalSeparator('.');
            dfs = local_dfs; // avoid race condition
        }

        final DecimalFormat this_df;
        // use singleton if on ui thread otherwise allocate new as DecimalFormat is not thread safe
        if (Thread.currentThread().getId() == 1) {
            if (df == null) {
                final DecimalFormat local_df = new DecimalFormat("#", dfs);
                local_df.setMinimumIntegerDigits(1);
                df = local_df; // avoid race condition
            }
            this_df = df;
        } else {
            this_df = new DecimalFormat("#", dfs);
        }

        this_df.setMaximumFractionDigits(digits);
        return this_df.format(x);
    }

}
