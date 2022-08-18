package info.nightscout.androidaps.plugins.general.autotune.data

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.extensions.blockValueBySeconds
import info.nightscout.androidaps.extensions.pureProfileFromJson
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class ATProfile(profile: Profile, var localInsulin: LocalInsulin, val injector: HasAndroidInjector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    var profile: ProfileSealed
    var circadianProfile: ProfileSealed
    lateinit var pumpProfile: ProfileSealed
    var profilename: String = ""
    var basal = DoubleArray(24)
    var basalUntuned = IntArray(24)
    var ic = 0.0
    var isf = 0.0
    var dia = 0.0
    var peak = 0
    var isValid: Boolean = false
    var from: Long = 0
    var pumpProfileAvgISF = 0.0
    var pumpProfileAvgIC = 0.0

    val icSize: Int
        get() = profile.getIcsValues().size
    val isfSize: Int
        get() = profile.getIsfsMgdlValues().size
    val avgISF: Double
        get() = if (profile.getIsfsMgdlValues().size == 1) profile.getIsfsMgdlValues().get(0).value else Round.roundTo(averageProfileValue(profile.getIsfsMgdlValues()), 0.01)
    val avgIC: Double
        get() = if (profile.getIcsValues().size == 1) profile.getIcsValues().get(0).value else Round.roundTo(averageProfileValue(profile.getIcsValues()), 0.01)

    fun getBasal(timestamp: Long): Double = basal[Profile.secondsFromMidnight(timestamp)/3600]

    // for localProfilePlugin Synchronisation
    fun basal() = jsonArray(basal)
    fun ic(circadian: Boolean = false): JSONArray {
        if(circadian)
            return jsonArray(pumpProfile.icBlocks, avgIC/pumpProfileAvgIC)
        return jsonArray(ic)
    }
    fun isf(circadian: Boolean = false): JSONArray {
        if(circadian)
            return jsonArray(pumpProfile.isfBlocks, avgISF/pumpProfileAvgISF)
        return jsonArray(Profile.fromMgdlToUnits(isf, profile.units))
    }

    fun getProfile(circadian: Boolean = false): PureProfile {
        return if (circadian)
            circadianProfile.convertToNonCustomizedProfile(dateUtil)
        else
            profile.convertToNonCustomizedProfile(dateUtil)
    }

    fun updateProfile() {
        data()?.let { profile = ProfileSealed.Pure(it) }
        data(true)?.let { circadianProfile = ProfileSealed.Pure(it) }
    }

    //Export json string with oref0 format used for autotune
    // Include min_5m_carbimpact, insulin type, single value for carb_ratio and isf
    fun profiletoOrefJSON(): String {
        var jsonString = ""
        val json = JSONObject()
       val insulinInterface: Insulin = activePlugin.activeInsulin
        try {
            json.put("name", profilename)
            json.put("min_5m_carbimpact", sp.getDouble("openapsama_min_5m_carbimpact", 3.0))
            json.put("dia", dia)
            if (insulinInterface.id === Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING) json.put(
                "curve",
                "ultra-rapid"
            ) else if (insulinInterface.id === Insulin.InsulinType.OREF_RAPID_ACTING) json.put("curve", "rapid-acting") else if (insulinInterface.id === Insulin.InsulinType.OREF_LYUMJEV) {
                json.put("curve", "ultra-rapid")
                json.put("useCustomPeakTime", true)
                json.put("insulinPeakTime", 45)
            } else if (insulinInterface.id === Insulin.InsulinType.OREF_FREE_PEAK) {
                val peaktime: Int = sp.getInt(rh.gs(R.string.key_insulin_oref_peak), 75)
                json.put("curve", if (peaktime > 50) "rapid-acting" else "ultra-rapid")
                json.put("useCustomPeakTime", true)
                json.put("insulinPeakTime", peaktime)
            }
            val basals = JSONArray()
            for (h in 0..23) {
                val secondfrommidnight = h * 60 * 60
                var time: String
                time = DecimalFormat("00").format(h) + ":00:00"
                basals.put(
                    JSONObject()
                        .put("start", time)
                        .put("minutes", h * 60)
                        .put("rate", profile.getBasalTimeFromMidnight(secondfrommidnight)
                        )
                )
            }
            json.put("basalprofile", basals)
            val isfvalue = Round.roundTo(avgISF, 0.001)
            json.put(
                "isfProfile",
                JSONObject().put(
                    "sensitivities",
                    JSONArray().put(JSONObject().put("i", 0).put("start", "00:00:00").put("sensitivity", isfvalue).put("offset", 0).put("x", 0).put("endoffset", 1440))
                )
            )
            json.put("carb_ratio", avgIC)
            json.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")))
            json.put("autosens_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_min, "0.7")))
            json.put("units", GlucoseUnit.MGDL.asText)
            json.put("timezone", TimeZone.getDefault().id)
            jsonString = json.toString(2).replace("\\/", "/")
        } catch (e: JSONException) {}

        return jsonString
    }

    fun data(circadian: Boolean = false): PureProfile? {
        val json: JSONObject = profile.toPureNsJson(dateUtil)
        try {
            json.put("dia", dia)
            if (circadian) {
                json.put("sens", jsonArray(pumpProfile.isfBlocks, avgISF/pumpProfileAvgISF))
                json.put("carbratio", jsonArray(pumpProfile.icBlocks, avgIC/pumpProfileAvgIC))
            } else {
                json.put("sens", jsonArray(Profile.fromMgdlToUnits(isf, profile.units)))
                json.put("carbratio", jsonArray(ic))
            }
            json.put("basal", jsonArray(basal))
        } catch (e: JSONException) {
        }
        return pureProfileFromJson(json, dateUtil, profile.units.asText)
    }

    fun profileStore(circadian: Boolean = false): ProfileStore?
    {
        var profileStore: ProfileStore? = null
        val json = JSONObject()
        val store = JSONObject()
        val tunedProfile = if (circadian) circadianProfile else profile
        if (profilename.isEmpty())
            profilename = rh.gs(R.string.autotune_tunedprofile_name)
        try {
            store.put(profilename, tunedProfile.toPureNsJson(dateUtil))
            json.put("defaultProfile", profilename)
            json.put("store", store)
            json.put("startDate", dateUtil.toISOAsUTC(dateUtil.now()))
            profileStore = ProfileStore(injector, json, dateUtil)
        } catch (e: JSONException) { }
        return profileStore
    }

    fun jsonArray(values: DoubleArray): JSONArray {
        val json = JSONArray()
        for (h in 0..23) {
            val secondfrommidnight = h * 60 * 60
            val df = DecimalFormat("00")
            val time = df.format(h.toLong()) + ":00"
            json.put(
                JSONObject()
                    .put("time", time)
                    .put("timeAsSeconds", secondfrommidnight)
                    .put("value", values[h])
            )
        }
        return json
    }

    fun jsonArray(value: Double) =
        JSONArray().put(
            JSONObject()
                .put("time", "00:00")
                .put("timeAsSeconds", 0)
                .put("value", value)
        )

    fun jsonArray(values: List<Block>, multiplier: Double = 1.0): JSONArray {
        val json = JSONArray()
        var elapsedHours = 0L
        values.forEach {
            val value = values.blockValueBySeconds(T.hours(elapsedHours).secs().toInt(), multiplier, 0)
            json.put(
                JSONObject()
                    .put("time", DecimalFormat("00").format(elapsedHours) + ":00")
                    .put("timeAsSeconds", T.hours(elapsedHours).secs())
                    .put("value", value)
            )
            elapsedHours += T.msecs(it.duration).hours()
        }
        return json
    }

    companion object {

        fun averageProfileValue(pf: Array<Profile.ProfileValue>?): Double {
            var avgValue = 0.0
            val secondPerDay = 24 * 60 * 60
            if (pf == null) return avgValue
            for (i in pf.indices) {
                avgValue += pf[i].value * ((if (i == pf.size - 1) secondPerDay else pf[i + 1].timeAsSeconds) - pf[i].timeAsSeconds)
            }
            avgValue /= secondPerDay.toDouble()
            return avgValue
        }
    }

    init {
        injector.androidInjector().inject(this)
        this.profile = profile as ProfileSealed
        circadianProfile = profile
        isValid = profile.isValid
        if (isValid) {
            //initialize tuned value with current profile values
            var minBasal = 1.0
            for (h in 0..23) {
                basal[h] = Round.roundTo(profile.basalBlocks.blockValueBySeconds(T.hours(h.toLong()).secs().toInt(), 1.0, 0), 0.001)
                minBasal = Math.min(minBasal, basal[h])
            }
            ic = avgIC
            isf = avgISF
            if (ic * isf * minBasal == 0.0)     // Additional validity check to avoid error later in AutotunePrep
                isValid = false
            pumpProfile = profile
            pumpProfileAvgIC = avgIC
            pumpProfileAvgISF = avgISF
        }
        dia = localInsulin.dia
        peak = localInsulin.peak
    }
}