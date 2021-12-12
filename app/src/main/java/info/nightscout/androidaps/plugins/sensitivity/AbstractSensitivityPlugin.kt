package info.nightscout.androidaps.plugins.sensitivity

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.Sensitivity
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.Round
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import kotlin.math.max
import kotlin.math.min

abstract class AbstractSensitivityPlugin(
    pluginDescription: PluginDescription,
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    val sp: SP
) : PluginBase(pluginDescription, aapsLogger, rh, injector), Sensitivity {

    abstract override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult

    fun fillResult(ratio: Double, carbsAbsorbed: Double, pastSensitivity: String,
                   ratioLimit: String, sensResult: String, deviationsArraySize: Int): AutosensResult {
        return fillResult(ratio, carbsAbsorbed, pastSensitivity, ratioLimit, sensResult,
            deviationsArraySize,
            SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_min, "0.7")),
            SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")))
    }

    fun fillResult(ratioParam: Double, carbsAbsorbed: Double, pastSensitivity: String,
                   ratioLimitParam: String, sensResult: String, deviationsArraySize: Int,
                   ratioMin: Double, ratioMax: Double): AutosensResult {
        var ratio = ratioParam
        var ratioLimit = ratioLimitParam
        val rawRatio = ratio
        ratio = max(ratio, ratioMin)
        ratio = min(ratio, ratioMax)

        //If not-excluded data <= MIN_HOURS -> don't do Autosens
        //If not-excluded data >= MIN_HOURS_FULL_AUTOSENS -> full Autosens
        //Between MIN_HOURS and MIN_HOURS_FULL_AUTOSENS: gradually increase autosens
        val autosensContrib = (min(max(Sensitivity.MIN_HOURS, deviationsArraySize / 12.0),
            Sensitivity.MIN_HOURS_FULL_AUTOSENS) - Sensitivity.MIN_HOURS) / (Sensitivity.MIN_HOURS_FULL_AUTOSENS - Sensitivity.MIN_HOURS)
        ratio = autosensContrib * (ratio - 1) + 1
        if (autosensContrib != 1.0) {
            ratioLimit += "(" + deviationsArraySize + " of " + Sensitivity.MIN_HOURS_FULL_AUTOSENS * 12 + " values) "
        }
        if (ratio != rawRatio) {
            ratioLimit += "Ratio limited from $rawRatio to $ratio"
            aapsLogger.debug(LTag.AUTOSENS, ratioLimit)
        }
        val output = AutosensResult()
        output.ratio = Round.roundTo(ratio, 0.01)
        output.carbsAbsorbed = Round.roundTo(carbsAbsorbed, 0.01)
        output.pastSensitivity = pastSensitivity
        output.ratioLimit = ratioLimit
        output.sensResult = sensResult
        return output
    }
}
