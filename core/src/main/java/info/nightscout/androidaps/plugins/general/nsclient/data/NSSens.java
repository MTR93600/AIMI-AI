package info.nightscout.androidaps.plugins.general.nsclient.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

/**
 * Created by Dirceu on 10/04/21.
 */
public class NSSens {
    private static final Logger log = StacktraceLoggerWrapper.getLogger(LTag.NSCLIENT);

    private final JSONObject data;

    public NSSens(JSONObject obj) {
        this.data = obj;
    }

    private String getStringOrNull(String key) {
        String ret = null;
        if (data.has(key)) {
            try {
                ret = data.getString(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Integer getIntegerOrNull(String key) {
        Integer ret = null;
        if (data.has(key)) {
            try {
                ret = data.getInt(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Long getLongOrNull(String key) {
        Long ret = null;
        if (data.has(key)) {
            try {
                ret = data.getLong(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }

    private Double getDoubleOrNull(String key) {
        Double ret = null;
        if (data.has(key)) {
            try {
                ret = data.getDouble(key);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return ret;
    }
    public JSONObject getData () { return data; }
    public Integer getSensorUptime () { return getIntegerOrNull("sensorUptime"); }
    public Long getMills () { return getLongOrNull("date"); }
    public String getDirection () { return getStringOrNull("direction"); }
    public String getId () { return getStringOrNull("_id"); }

    public Double getCalibrationFactor() {
        return getDoubleOrNull("calibrationFactor");
    }

    public Double getIsig() {
        return getDoubleOrNull("isig");
    }

    public Double getBG() {
        return getDoubleOrNull("value");
    }

    public double getDeltaSinceLastBG() {
        return getDoubleOrNull("delta");
    }
}
