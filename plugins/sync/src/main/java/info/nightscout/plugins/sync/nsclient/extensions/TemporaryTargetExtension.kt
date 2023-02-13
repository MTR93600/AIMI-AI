package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.json.JSONObject

fun temporaryTargetFromJson(jsonObject: JSONObject): TemporaryTarget? {
    val units = GlucoseUnit.fromText(JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL))
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration", null) ?: return null
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    var low = JsonHelper.safeGetDouble(jsonObject, "targetBottom")
    low = Profile.toMgdl(low, units)
    var high = JsonHelper.safeGetDouble(jsonObject, "targetTop")
    high = Profile.toMgdl(high, units)
    val reasonString = if (duration != 0L) JsonHelper.safeGetStringAllowNull(jsonObject, "reason", null)
        ?: return null else ""
    // this string can be localized from NS, it will not work in this case CUSTOM will be used
    val reason = TemporaryTarget.Reason.fromString(reasonString)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    if (duration > 0L) {
        // not ending event
        if (low < Constants.MIN_TT_MGDL) return null
        if (low > Constants.MAX_TT_MGDL) return null
        if (high < Constants.MIN_TT_MGDL) return null
        if (high > Constants.MAX_TT_MGDL) return null
        if (low > high) return null
    }
    val tt = TemporaryTarget(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        reason = reason,
        lowTarget = low,
        highTarget = high,
        isValid = isValid
    )
    tt.interfaceIDs.nightscoutId = id
    return tt
}

fun TemporaryTarget.toJson(isAdd: Boolean, units: GlucoseUnit, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", info.nightscout.database.entities.TherapyEvent.Type.TEMPORARY_TARGET.text)
        .put("duration", T.msecs(duration).mins())
        .put("durationInMilliseconds", duration)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("timestamp", timestamp)
        .put("enteredBy", "AndroidAPS").also {
            if (lowTarget > 0) it
                .put("reason", reason.text)
                .put("targetBottom", Profile.fromMgdlToUnits(lowTarget, units))
                .put("targetTop", Profile.fromMgdlToUnits(highTarget, units))
                .put("units", units.asText)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }
