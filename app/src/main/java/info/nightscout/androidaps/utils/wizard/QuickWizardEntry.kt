package info.nightscout.androidaps.utils.wizard

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetInt
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class QuickWizardEntry @Inject constructor(private val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

    lateinit var storage: JSONObject
    var position: Int = -1

    companion object {

        const val YES = 0
        const val NO = 1
        private const val POSITIVE_ONLY = 2
        private const val NEGATIVE_ONLY = 3
    }

    init {
        injector.androidInjector().inject(this)
        val emptyData = "{\"buttonText\":\"\",\"carbs\":0,\"validFrom\":0,\"validTo\":86340}"
        try {
            storage = JSONObject(emptyData)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    /*
        {
            buttonText: "Meal",
            carbs: 36,
            validFrom: 8 * 60 * 60, // seconds from midnight
            validTo: 9 * 60 * 60,   // seconds from midnight
            useBG: 0,
            useCOB: 0,
            useBolusIOB: 0,
            useBasalIOB: 0,
            useTrend: 0,
            useSuperBolus: 0,
            useTemptarget: 0
        }
     */
    fun from(entry: JSONObject, position: Int): QuickWizardEntry {
        storage = entry
        this.position = position
        return this
    }

    fun isActive(): Boolean = profileFunction.secondsFromMidnight() >= validFrom() && profileFunction.secondsFromMidnight() <= validTo()

    fun doCalc(profile: Profile, profileName: String, lastBG: GlucoseValue, _synchronized: Boolean): BolusWizard {
        val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        val tempTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null
        //BG
        var bg = 0.0
        if (useBG() == YES) {
            bg = lastBG.valueToUnits(profileFunction.getUnits())
        }
        // COB
        val cob =
            if (useCOB() == YES) iobCobCalculator.getCobInfo(_synchronized, "QuickWizard COB").displayCob ?: 0.0
            else 0.0
        // Bolus IOB
        var bolusIOB = false
        if (useBolusIOB() == YES) {
            bolusIOB = true
        }
        // Basal IOB
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        var basalIOB = false
        if (useBasalIOB() == YES) {
            basalIOB = true
        } else if (useBasalIOB() == POSITIVE_ONLY && basalIob.iob > 0) {
            basalIOB = true
        } else if (useBasalIOB() == NEGATIVE_ONLY && basalIob.iob < 0) {
            basalIOB = true
        }
        // SuperBolus
        var superBolus = false
        if (useSuperBolus() == YES && sp.getBoolean(R.string.key_usesuperbolus, false)) {
            superBolus = true
        }
        if ((loop as PluginBase).isEnabled() && loop.isSuperBolus) superBolus = false
        // Trend
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        var trend = false
        if (useTrend() == YES) {
            trend = true
        } else if (useTrend() == POSITIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta > 0) {
            trend = true
        } else if (useTrend() == NEGATIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta < 0) {
            trend = true
        }
        val percentage = sp.getInt(R.string.key_boluswizard_percentage, 100)
        return BolusWizard(injector).doCalc(profile, profileName, tempTarget, carbs(), cob, bg, 0.0, percentage, true, useCOB() == YES, bolusIOB, basalIOB, superBolus, useTempTarget() == YES, trend, false, buttonText(), quickWizard = true) //tbc, ok if only quickwizard, but if other sources elsewhere use Sources.QuickWizard
    }

    fun buttonText(): String = safeGetString(storage, "buttonText", "")

    fun carbs(): Int = safeGetInt(storage, "carbs")

    fun validFromDate(): Long = dateUtil.secondsOfTheDayToMilliseconds(validFrom())

    fun validToDate(): Long = dateUtil.secondsOfTheDayToMilliseconds(validTo())

    fun validFrom(): Int = safeGetInt(storage, "validFrom")

    fun validTo(): Int = safeGetInt(storage, "validTo")

    fun useBG(): Int = safeGetInt(storage, "useBG", YES)

    fun useCOB(): Int = safeGetInt(storage, "useCOB", NO)

    fun useBolusIOB(): Int = safeGetInt(storage, "useBolusIOB", YES)

    fun useBasalIOB(): Int = safeGetInt(storage, "useBasalIOB", YES)

    fun useTrend(): Int = safeGetInt(storage, "useTrend", NO)

    fun useSuperBolus(): Int = safeGetInt(storage, "useSuperBolus", NO)

    fun useTempTarget(): Int = safeGetInt(storage, "useTempTarget", NO)
}