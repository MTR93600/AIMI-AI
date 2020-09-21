package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;

public class IobTotal implements DataPointWithLabelInterface {

    public double iob;
    public double activity;
    public double bolussnooze;
    public double basaliob;
    public double netbasalinsulin;
    public double hightempinsulin;

    // oref1
    public long lastBolusTime;
    public IobTotal iobWithZeroTemp;

    public double netInsulin = 0d; // for calculations from temp basals only
    public double netRatio = 0d; // net ratio at start of temp basal

    public double extendedBolusInsulin = 0d; // total insulin for extended bolus

    long time;


    public IobTotal copy() {
        IobTotal i = new IobTotal(time);
        i.iob = iob;
        i.activity = activity;
        i.bolussnooze = bolussnooze;
        i.basaliob = basaliob;
        i.netbasalinsulin = netbasalinsulin;
        i.hightempinsulin = hightempinsulin;
        i.lastBolusTime = lastBolusTime;
        if (iobWithZeroTemp != null) i.iobWithZeroTemp = iobWithZeroTemp.copy();
        i.netInsulin = netInsulin;
        i.netRatio = netRatio;
        i.extendedBolusInsulin = extendedBolusInsulin;
        return i;
    }

    public IobTotal(long time) {
        this.iob = 0d;
        this.activity = 0d;
        this.bolussnooze = 0d;
        this.basaliob = 0d;
        this.netbasalinsulin = 0d;
        this.hightempinsulin = 0d;
        this.lastBolusTime = 0;
        this.time = time;
    }

    public IobTotal plus(IobTotal other) {
        iob += other.iob;
        activity += other.activity;
        bolussnooze += other.bolussnooze;
        basaliob += other.basaliob;
        netbasalinsulin += other.netbasalinsulin;
        hightempinsulin += other.hightempinsulin;
        netInsulin += other.netInsulin;
        extendedBolusInsulin += other.extendedBolusInsulin;
        return this;
    }

    public static IobTotal combine(IobTotal bolusIOB, IobTotal basalIob) {
        IobTotal result = new IobTotal(bolusIOB.time);
        result.iob = bolusIOB.iob + basalIob.basaliob;
        result.activity = bolusIOB.activity + basalIob.activity;
        result.bolussnooze = bolusIOB.bolussnooze;
        result.basaliob = bolusIOB.basaliob + basalIob.basaliob;
        result.netbasalinsulin = bolusIOB.netbasalinsulin + basalIob.netbasalinsulin;
        result.hightempinsulin = basalIob.hightempinsulin + bolusIOB.hightempinsulin;
        result.netInsulin = basalIob.netInsulin + bolusIOB.netInsulin;
        result.extendedBolusInsulin = basalIob.extendedBolusInsulin + bolusIOB.extendedBolusInsulin;
        result.lastBolusTime = bolusIOB.lastBolusTime;
        result.iobWithZeroTemp = basalIob.iobWithZeroTemp;
        return result;
    }

    public IobTotal round() {
        this.iob = Round.roundTo(this.iob, 0.001);
        this.activity = Round.roundTo(this.activity, 0.0001);
        this.bolussnooze = Round.roundTo(this.bolussnooze, 0.0001);
        this.basaliob = Round.roundTo(this.basaliob, 0.001);
        this.netbasalinsulin = Round.roundTo(this.netbasalinsulin, 0.001);
        this.hightempinsulin = Round.roundTo(this.hightempinsulin, 0.001);
        this.netInsulin = Round.roundTo(this.netInsulin, 0.001);
        this.extendedBolusInsulin = Round.roundTo(this.extendedBolusInsulin, 0.001);
        return this;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("activity", activity);
            json.put("time", DateUtil.toISOString(new Date()));
        } catch (JSONException ignored) {
        }
        return json;
    }

    public JSONObject determineBasalJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("bolussnooze", bolussnooze);
            json.put("activity", activity);
            json.put("lastBolusTime", lastBolusTime);
            json.put("time", DateUtil.toISOString(new Date(time)));
            /*

            This is requested by SMB determine_basal but by based on Scott's info
            it's MDT specific safety check only
            It's causing rounding issues in determine_basal

            JSONObject lastTemp = new JSONObject();
            lastTemp.put("date", lastTempDate);
            lastTemp.put("rate", lastTempRate);
            lastTemp.put("duration", lastTempDuration);
            json.put("lastTemp", lastTemp);
            */
            if (iobWithZeroTemp != null) {
                JSONObject iwzt = iobWithZeroTemp.determineBasalJson();
                json.put("iobWithZeroTemp", iwzt);
            }
        } catch (JSONException ignored) {
        }
        return json;
    }

    // DataPoint interface

    private int color;

    @Override
    public double getX() {
        return time;
    }

    @Override
    public double getY() {
        return iob;
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
        return PointsWithLabelGraphSeries.Shape.IOBPREDICTION;
    }

    @Override
    public float getSize() {
        return 0.5f;
    }

    @Override
    public int getColor() {
        return color;
    }

    public IobTotal setColor(int color) {
        this.color = color;
        return this;
    }
}
