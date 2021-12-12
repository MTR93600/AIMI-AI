package info.nightscout.androidaps.plugins.sensitivity

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.isPSEvent5minBack
import info.nightscout.androidaps.extensions.isTherapyEventEvent5minBack
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.Sensitivity.SensitivityType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@OpenForTesting
@Singleton
class SensitivityAAPSPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    sp: SP,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : AbstractSensitivityPlugin(
    PluginDescription()
        .mainType(PluginType.SENSITIVITY)
        .pluginIcon(R.drawable.ic_generic_icon)
        .pluginName(R.string.sensitivityaaps)
        .shortName(R.string.sensitivity_shortname)
        .preferencesId(R.xml.pref_absorption_aaps)
        .description(R.string.description_sensitivity_aaps),
    injector, aapsLogger, rh, sp
) {

    override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
        val age = sp.getString(R.string.key_age, "")
        var defaultHours = 24
        if (age == rh.gs(R.string.key_adult)) defaultHours = 24
        if (age == rh.gs(R.string.key_teenage)) defaultHours = 4
        if (age == rh.gs(R.string.key_child)) defaultHours = 4
        val hoursForDetection = sp.getInt(R.string.key_openapsama_autosens_period, defaultHours)
        val profile = profileFunction.getProfile()
        if (profile == null) {
            aapsLogger.error("No profile")
            return AutosensResult()
        }
        if (ads.autosensDataTable.size() < 4) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val current = ads.getAutosensDataAtTime(toTime) // this is running inside lock already
        if (current == null) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val siteChanges = repository.getTherapyEventDataFromTime(fromTime, TherapyEvent.Type.CANNULA_CHANGE, true).blockingGet()
        val profileSwitches = repository.getProfileSwitchDataFromTime(fromTime, true).blockingGet()
        val deviationsArray: MutableList<Double> = ArrayList()
        var pastSensitivity = ""
        var index = 0
        while (index < ads.autosensDataTable.size()) {
            val autosensData = ads.autosensDataTable.valueAt(index)
            if (autosensData.time < fromTime) {
                index++
                continue
            }
            if (autosensData.time > toTime) {
                index++
                continue
            }

            // reset deviations after site change
            if (siteChanges.isTherapyEventEvent5minBack(autosensData.time)) {
                deviationsArray.clear()
                pastSensitivity += "(SITECHANGE)"
            }

            // reset deviations after profile switch
            if (profileSwitches.isPSEvent5minBack(autosensData.time)) {
                deviationsArray.clear()
                pastSensitivity += "(PROFILESWITCH)"
            }
            var deviation = autosensData.deviation

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0) deviation = 0.0
            if (autosensData.validDeviation) if (autosensData.time > toTime - hoursForDetection * 60 * 60 * 1000L) deviationsArray.add(deviation)
            if (deviationsArray.size > hoursForDetection * 60 / 5) deviationsArray.removeAt(0)
            pastSensitivity += autosensData.pastSensitivity
            val secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time)
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + (secondsFromMidnight / 3600.0).roundToInt() + ")"
            }
            index++
        }
        val deviations = Array(deviationsArray.size) { i -> deviationsArray[i] }
        val sens = profile.getIsfMgdl()
        val ratioLimit = ""
        val sensResult: String
        aapsLogger.debug(LTag.AUTOSENS, "Records: $index   $pastSensitivity")
        Arrays.sort(deviations)
        val percentile = IobCobCalculatorPlugin.percentile(deviations, 0.50)
        val basalOff = percentile * (60.0 / 5.0) / sens
        val ratio = 1 + basalOff / profile.getMaxDailyBasal()
        sensResult = when {
            percentile < 0 -> "Excess insulin sensitivity detected"
            percentile > 0 -> "Excess insulin resistance detected"
            else           -> "Sensitivity normal"

        }
        aapsLogger.debug(LTag.AUTOSENS, sensResult)
        val output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
            sensResult, deviationsArray.size)
        aapsLogger.debug(
            LTag.AUTOSENS, "Sensitivity to: "
            + dateUtil.dateAndTimeString(toTime) +
            " ratio: " + output.ratio
            + " mealCOB: " + current.cob)
        aapsLogger.debug(LTag.AUTOSENS, "Sensitivity to: deviations " + deviations.contentToString())
        return output
    }

    override val id: SensitivityType
        get() = SensitivityType.SENSITIVITY_AAPS

    override fun configuration(): JSONObject {
        val c = JSONObject()
        try {
            c.put(rh.gs(R.string.key_absorption_maxtime), sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME))
            c.put(rh.gs(R.string.key_openapsama_autosens_period), sp.getInt(R.string.key_openapsama_autosens_period, 24))
            c.put(rh.gs(R.string.key_openapsama_autosens_max), sp.getDouble(R.string.key_openapsama_autosens_max, 1.2))
            c.put(rh.gs(R.string.key_openapsama_autosens_min), sp.getDouble(R.string.key_openapsama_autosens_min, 0.7))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return c
    }

    override fun applyConfiguration(configuration: JSONObject) {
        try {
            if (configuration.has(rh.gs(R.string.key_absorption_maxtime))) sp.putDouble(R.string.key_absorption_maxtime, configuration.getDouble(rh.gs(R.string.key_absorption_maxtime)))
            if (configuration.has(rh.gs(R.string.key_openapsama_autosens_period))) sp.putDouble(R.string.key_openapsama_autosens_period, configuration.getDouble(rh.gs(R.string.key_openapsama_autosens_period)))
            if (configuration.has(rh.gs(R.string.key_openapsama_autosens_max))) sp.getDouble(R.string.key_openapsama_autosens_max, configuration.getDouble(rh.gs(R.string.key_openapsama_autosens_max)))
            if (configuration.has(rh.gs(R.string.key_openapsama_autosens_min))) sp.getDouble(R.string.key_openapsama_autosens_min, configuration.getDouble(rh.gs(R.string.key_openapsama_autosens_min)))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}