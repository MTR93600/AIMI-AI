package info.nightscout.plugins.aps.aimi

import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.getPassedDurationToTimeInMinutes
import info.nightscout.core.extensions.plannedRemainingMinutes
import info.nightscout.database.ValueWrapper
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.interfaces.aps.AIMIDefaults
import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.iob.MealData
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.logger.LoggerCallback
import info.nightscout.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.plugins.aps.utils.ScriptReader
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.math.ln
import info.nightscout.shared.utils.T
import info.nightscout.shared.utils.DateUtil
import info.nightscout.interfaces.stats.TirCalculator
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.entities.Bolus


class DetermineBasalAdapterAIMIJS internal constructor(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector): DetermineBasalAdapter {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    



    private var profile = JSONObject()
    private var mGlucoseStatus = JSONObject()
    private var iobData: JSONArray? = null
    private var mealData = JSONObject()
    private var currentTemp = JSONObject()
    private var autosensData = JSONObject()
    private var microBolusAllowed = false
    private var smbAlwaysAllowed = false
    private var currentTime: Long = 0
    private var flatBGsDetected = false
    private val millsToThePast = T.hours(1).msecs()
    private var lastBolusNormalTimecount: Long = 0
    private var lastBolusSMBcount: Long = 0
    private var lastSMBmscount: Long = 0

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""



    @Suppress("SpellCheckingInspection")
    override operator fun invoke(): DetermineBasalResultSMB? {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")
        aapsLogger.debug(LTag.APS, "Glucose status: " + mGlucoseStatus.toString().also { glucoseStatusParam = it })
        aapsLogger.debug(LTag.APS, "IOB data:       " + iobData.toString().also { iobDataParam = it })
        aapsLogger.debug(LTag.APS, "Current temp:   " + currentTemp.toString().also { currentTempParam = it })
        aapsLogger.debug(LTag.APS, "Profile:        " + profile.toString().also { profileParam = it })
        aapsLogger.debug(LTag.APS, "Meal data:      " + mealData.toString().also { mealDataParam = it })
        aapsLogger.debug(LTag.APS, "Autosens data:  $autosensData")
        aapsLogger.debug(LTag.APS, "Reservoir data: " + "undefined")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "SMBAlwaysAllowed:  $smbAlwaysAllowed")
        aapsLogger.debug(LTag.APS, "CurrentTime: $currentTime")
        aapsLogger.debug(LTag.APS, "flatBGsDetected: $flatBGsDetected")
        var determineBasalResultSMB: DetermineBasalResultSMB? = null
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        // Turn off optimization to make Rhino Android compatible
        rhino.optimizationLevel = -1
        try {

            //register logger callback for console.log and console.error
            ScriptableObject.defineClass(scope, LoggerCallback::class.java)
            val myLogger = rhino.newObject(scope, "LoggerCallback", null)
            scope.put("console2", scope, myLogger)
            rhino.evaluateString(scope, readFile("OpenAPSAMA/loggerhelper.js"), "JavaScript", 0, null)

            //set module parent
            rhino.evaluateString(scope, "var module = {\"parent\":Boolean(1)};", "JavaScript", 0, null)
            rhino.evaluateString(scope, "var round_basal = function round_basal(basal, profile) { return basal; };", "JavaScript", 0, null)
            rhino.evaluateString(scope, "require = function() {return round_basal;};", "JavaScript", 0, null)

            //generate functions "determine_basal" and "setTempBasal"
            //rhino.evaluateString(scope, readFile(LoopVariantPreference.getVariantFileName(sp, AIMIDefaults.folderName)), "JavaScript", 0, null)
            if (sp.getBoolean(R.string.key_use_b30mssv, false))
                rhino.evaluateString(scope, readFile("AIMI/b30mssv/determine-basal.js"), "JavaScript", 0, null)
            else if (sp.getBoolean(R.string.key_use_mssv, false))
                rhino.evaluateString(scope, readFile("AIMI/mssv/determine-basal.js"), "JavaScript", 0, null)
            else if (sp.getBoolean(R.string.key_use_pammssv, false))
                rhino.evaluateString(scope, readFile("AIMI/pammssv/determine-basal.js"), "JavaScript", 0, null)
            else
                rhino.evaluateString(scope, readFile("AIMI/pam/determine-basal.js"), "JavaScript", 0, null)


            rhino.evaluateString(scope, readFile("OpenAPSSMB/basal-set-temp.js"), "setTempBasal.js", 0, null)
            val determineBasalObj = scope["determine_basal", scope]
            val setTempBasalFunctionsObj = scope["tempBasalFunctions", scope]

            //call determine-basal
            if (determineBasalObj is Function && setTempBasalFunctionsObj is NativeObject) {

                //prepare parameters
                val params = arrayOf(
                    makeParam(mGlucoseStatus, rhino, scope),
                    makeParam(currentTemp, rhino, scope),
                    makeParamArray(iobData, rhino, scope),
                    makeParam(profile, rhino, scope),
                    makeParam(autosensData, rhino, scope),
                    makeParam(mealData, rhino, scope),
                    setTempBasalFunctionsObj,
                    java.lang.Boolean.valueOf(microBolusAllowed),
                    makeParam(null, rhino, scope),  // reservoir data as undefined
                    java.lang.Long.valueOf(currentTime),
                    java.lang.Boolean.valueOf(flatBGsDetected)
                )
                val jsResult = determineBasalObj.call(rhino, scope, scope, params) as NativeObject
                scriptDebug = LoggerCallback.scriptDebug

                // Parse the jsResult object to a JSON-String
                val result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString()
                aapsLogger.debug(LTag.APS, "Result: $result")
                try {
                    val resultJson = JSONObject(result)
                    determineBasalResultSMB = DetermineBasalResultSMB(injector, resultJson)
                } catch (e: JSONException) {
                    aapsLogger.error(LTag.APS, "Unhandled exception", e)
                }
            } else {
                aapsLogger.error(LTag.APS, "Problem loading JS Functions")
            }
        } catch (e: IOException) {
            aapsLogger.error(LTag.APS, "IOException")
        } catch (e: RhinoException) {
            aapsLogger.error(LTag.APS, "RhinoException: (" + e.lineNumber() + "," + e.columnNumber() + ") " + e.toString())
        } catch (e: IllegalAccessException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InstantiationException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InvocationTargetException) {
            aapsLogger.error(LTag.APS, e.toString())
        } finally {
            Context.exit()
        }
        glucoseStatusParam = mGlucoseStatus.toString()
        iobDataParam = iobData.toString()
        currentTempParam = currentTemp.toString()
        profileParam = profile.toString()
        mealDataParam = mealData.toString()
        return determineBasalResultSMB
    }

    @Suppress("SpellCheckingInspection")
    override fun setData(
        profile: Profile,
        maxIob: Double,
        maxBasal: Double,
        minBg: Double,
        maxBg: Double,
        targetBg: Double,
        basalRate: Double,
        iobArray: Array<IobTotal>,
        glucoseStatus: GlucoseStatus,
        mealData: MealData,
        autosensDataRatio: Double,
        tempTargetSet: Boolean,
        microBolusAllowed: Boolean,
        uamAllowed: Boolean,
        advancedFiltering: Boolean,
        flatBGsDetected: Boolean
    ) {
        val pump = activePlugin.activePump
        val pumpBolusStep = pump.pumpDescription.bolusStep
        this.profile.put("max_iob", maxIob)
        this.profile.put("dia", profile.dia)
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg)
        this.profile.put("max_bg", maxBg)
        this.profile.put("target_bg", targetBg)
        this.profile.put("carb_ratio", profile.getIc())
        this.profile.put("sens", profile.getIsfMgdl())
        this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))

        val insulin = activePlugin.activeInsulin
        val insulinType = insulin.friendlyName
        val insulinPeak = insulin.peak
        this.profile.put("insulinType", insulinType)
        this.profile.put("insulinPeak", insulinPeak)
        //mProfile.put("high_temptarget_raises_sensitivity", SP.getBoolean(R.string.key_high_temptarget_raises_sensitivity, UAMDefaults.high_temptarget_raises_sensitivity));
//**********************************************************************************************************************************************
        //this.profile.put("high_temptarget_raises_sensitivity", false)
        //mProfile.put("low_temptarget_lowers_sensitivity", SP.getBoolean(R.string.key_low_temptarget_lowers_sensitivity, UAMDefaults.low_temptarget_lowers_sensitivity));
        this.profile.put("high_temptarget_raises_sensitivity",sp.getBoolean(info.nightscout.core.utils.R.string.key_high_temptarget_raises_sensitivity, AIMIDefaults.high_temptarget_raises_sensitivity))
        this.profile.put("low_temptarget_lowers_sensitivity",sp.getBoolean(info.nightscout.core.utils.R.string.key_low_temptarget_lowers_sensitivity, AIMIDefaults.low_temptarget_lowers_sensitivity))
        //this.profile.put("low_temptarget_lowers_sensitivity", false)
//**********************************************************************************************************************************************
        this.profile.put("sensitivity_raises_target", sp.getBoolean(R.string.key_sensitivity_raises_target, AIMIDefaults.sensitivity_raises_target))
        this.profile.put("resistance_lowers_target", sp.getBoolean(R.string.key_resistance_lowers_target, AIMIDefaults.resistance_lowers_target))
        this.profile.put("adv_target_adjustments", AIMIDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", AIMIDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", AIMIDefaults.half_basal_exercise_target)
        this.profile.put("maxCOB", AIMIDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, UAMDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", AIMIDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)

        this.profile.put("A52_risk_enable", AIMIDefaults.A52_risk_enable)
        val smbEnabled = sp.getBoolean(R.string.key_use_smb, false)
        this.profile.put("SMBInterval", sp.getInt(R.string.key_smb_interval, AIMIDefaults.SMBInterval))
        this.profile.put("enableSMB_with_COB", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_COB, false))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_temptarget, false))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && sp.getBoolean(R.string.key_allowSMB_with_high_temptarget, false))
        this.profile.put("enableSMB_always", smbEnabled && sp.getBoolean(R.string.key_enableSMB_always, false) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && sp.getBoolean(R.string.key_enableSMB_after_carbs, false) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", sp.getInt(R.string.key_smb_max_minutes, AIMIDefaults.maxSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uam_smb_max_minutes, AIMIDefaults.maxUAMSMBBasalMinutes))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", sp.getInt(R.string.key_carbsReqThreshold, AIMIDefaults.carbsReqThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", SafeParse.stringToDouble(sp.getString(info.nightscout.core.utils.R.string.key_openapsama_autosens_max, "1.2")))
//**********************************************************************************************************************************************

        this.profile.put("iTime",SafeParse.stringToDouble(sp.getString(R.string.key_iTime,"180")))
        this.profile.put("b30_upperBG",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_upperBG,"150")))
        this.profile.put("b30_duration",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_duration,"20")))
        this.profile.put("b30_upperdelta",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_upperdelta,"6")))
        this.profile.put("enable_AIMI_protein", sp.getBoolean(R.string.key_use_Aimiprotein, false))
        this.profile.put("b30_protein_start",SafeParse.stringToDouble(sp.getString(R.string.key_aimi_B30_proteinstart,"60")))
        this.profile.put("b30_protein_duration",SafeParse.stringToDouble(sp.getString(R.string.key_aimi_B30_proteinduration,"60")))
        this.profile.put("b30_protein_percent",SafeParse.stringToDouble(sp.getString(R.string.key_aimi_B30_proteinpercent,"300")))
        this.profile.put("iTime_Start_Bolus",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Starting_Bolus,"2")))
        this.profile.put("smb_delivery_ratio", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio, "0.5")))
        this.profile.put("smb_delivery_ratio_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_min, "0.5")))
        this.profile.put("smb_delivery_ratio_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_max, "0.9")))
        this.profile.put("smb_delivery_ratio_bg_range", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_bg_range, "40")))
        this.profile.put("smb_max_range_extension", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_max_range_extension, "1.2")))
        this.profile.put("enable_AIMI_UAM", sp.getBoolean(R.string.key_use_AimiUAM, false))
        this.profile.put("enable_AIMI_Break", sp.getBoolean(R.string.key_use_AimiBreak, false))
        this.profile.put("enable_AIMI_Power", sp.getBoolean(R.string.key_use_AimiPower, false))
        this.profile.put("key_use_AimiIOBpredBG", sp.getBoolean(R.string.key_use_AimiIOBpredBG, false))
        //this.profile.put("enable_AIMI_UAM_U200", sp.getBoolean(R.string.key_use_LuymjevU200, false))
        //this.profile.put("enable_AIMI_UAM_U100", sp.getBoolean(R.string.key_use_LuymjevU100, false))
        //this.profile.put("enable_AIMI_UAM_Fiasp", sp.getBoolean(R.string.key_use_Fiasp, false))
        //this.profile.put("enable_AIMI_UAM_Novorapid", sp.getBoolean(R.string.key_use_Novorapid, false))
        this.profile.put("key_use_AimiUAM_ISF", sp.getBoolean(R.string.key_use_AimiUAM_ISF, false))
        this.profile.put("key_use_AIMI_BreakFastLight", sp.getBoolean(R.string.key_use_AIMI_BreakFastLight, false))
        this.profile.put("key_use_disable_b30_BFL", sp.getBoolean(R.string.key_use_disable_b30_BFL, false))
        this.profile.put("key_AIMI_BreakFastLight_timestart", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timestart, "6")))
        this.profile.put("key_AIMI_BreakFastLight_timeend", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timeend, "10")))
        this.profile.put("key_use_AIMI_CAP", SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "150")))
        //this.profile.put("key_insulin_oref_peak", SafeParse.stringToDouble(sp.getString(R.string.key_insulin_oref_peak, "35")))


//**********************************************************************************************************************************************
        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }
        val now = System.currentTimeMillis()
        val tb = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.convertedToAbsolute(now, profile) ?: 0.0)
        // as we have non default temps longer than 30 mintues
        if (tb != null) currentTemp.put("minutesrunning", tb.getPassedDurationToTimeInMinutes(now))
        iobData = iobCobCalculator.convertToJSONArray(iobArray)
        mGlucoseStatus.put("glucose", glucoseStatus.glucose)

        mGlucoseStatus.put("noise", glucoseStatus.noise)
        if (sp.getBoolean(R.string.key_always_use_shortavg, false)) {
            mGlucoseStatus.put("delta", glucoseStatus.shortAvgDelta)
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta)
        }

        mGlucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        mGlucoseStatus.put("date", glucoseStatus.date)
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("mealCOB", mealData.mealCOB)
        this.mealData.put("slopeFromMaxDeviation", mealData.slopeFromMaxDeviation)
        this.mealData.put("slopeFromMinDeviation", mealData.slopeFromMinDeviation)
        this.mealData.put("lastBolusTime", mealData.lastBolusTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)

        val getlastBolusNormal = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL).blockingGet()
        val lastBolusNormalUnits = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.amount else 0L
        val lastBolusNormalTime = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.timestamp else 0L
        this.mealData.put("lastBolusNormalUnits", lastBolusNormalUnits)
        this.mealData.put("lastBolusNormalTime", lastBolusNormalTime)

        val bolusMealLinks = repository.getBolusesDataFromTime(now - millsToThePast, false).blockingGet()
        bolusMealLinks.forEach { bolus ->
            if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.amount >= SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Starting_Bolus, "2"))) lastBolusNormalTimecount += 1
            if (bolus.type == Bolus.Type.SMB && bolus.isValid) lastBolusSMBcount += 1
            if (bolus.type == Bolus.Type.SMB && bolus.isValid && bolus.amount == SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "3")) ) lastSMBmscount += 1
        }
        this.mealData.put("countBolus", lastBolusNormalTimecount)
        this.mealData.put("countSMB", lastBolusSMBcount)
        this.mealData.put("countSMBms", lastSMBmscount)

        val getlastBolusSMB = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.SMB).blockingGet()
        val lastBolusSMBUnits = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.amount else 0L
        val lastBolusSMBTime = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.timestamp else 0L
        this.mealData.put("lastBolusSMBUnits", lastBolusSMBUnits)
        this.mealData.put("lastBolusSMBTime", lastBolusSMBTime)

        val lastHourTIRAbove = tirCalculator.averageTIR(tirCalculator.calculateHour(80.0, 180.0))?.abovePct()
        val last2HourTIRAbove = tirCalculator.averageTIR(tirCalculator.calculate2Hour(80.0, 180.0))?.abovePct()
        val lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(80.0, 180.0))?.belowPct()
        val tdd1D = tddCalculator.averageTDD(tddCalculator.calculate(1))?.totalAmount
        val tdd7D = tddCalculator.averageTDD(tddCalculator.calculate(7))?.totalAmount
        val tddLast24H = tddCalculator.calculateDaily(-24, 0).totalAmount
        val tddLast4H = tddCalculator.calculateDaily(-4, 0).totalAmount
        val tddLast8to4H = tddCalculator.calculateDaily(-8, -4).totalAmount
        val tddLast24to23H = tddCalculator.calculateDaily(-24, -23).totalAmount
        val tddLast48to47H = tddCalculator.calculateDaily(-48, -47).totalAmount
        val tddLast72to71H = tddCalculator.calculateDaily(-72, -71).totalAmount
        val tddLast96to95H = tddCalculator.calculateDaily(-96, -95).totalAmount
        val tddlastHaverage = (tddLast24to23H+tddLast48to47H+tddLast72to71H+tddLast96to95H)/4

        val tddWeightedFromLast8H = ((1.4 * tddLast4H) + (0.6 * tddLast8to4H)) * 3
        var tdd =
            if (tdd1D != null && tdd7D != null && lastHourTIRLow!! > 0 && tdd7D != 0.0) ((tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)) * 0.85
            else if (tdd1D != null && tdd7D != null && tdd7D != 0.0 && lastHourTIRAbove!! > 0 && last2HourTIRAbove!! > 0) ((tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)) * 1.15
            else if (tdd1D != null && tdd7D != null && tdd7D != 0.0) (tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)
            else tddWeightedFromLast8H


        val aimisensitivity = if (tdd7D!= null && tdd7D != 0.0) tddLast24H / tdd7D else 1

        val insulinDivisor = when {
            insulin.peak >= 35 -> 55 // lyumjev peak: 45
            insulin.peak > 45 -> 65 // ultra rapid peak: 55
            else              -> 75 // rapid peak: 75
        }
        var variableSensitivity = 1800 / (tdd * (ln((glucoseStatus.glucose / insulinDivisor) + 1)))
        variableSensitivity = Round.roundTo(variableSensitivity, 0.1)

        this.profile.put("variable_sens", variableSensitivity)
        this.profile.put("lastHourTIRLow", lastHourTIRLow)
        this.profile.put("TDD", tdd)
        this.profile.put("lastHourTIRAbove", lastHourTIRAbove)
        this.profile.put("last2HourTIRAbove", last2HourTIRAbove)
        this.profile.put("aimisensitivity", aimisensitivity)
        this.profile.put("insulinDivisor", insulinDivisor)
        this.profile.put("tddlastHaverage", tddlastHaverage)
        this.profile.put("key_use_AimiIgnoreCOB", sp.getBoolean(R.string.key_use_AimiIgnoreCOB, false))

        //tddAIMI = TddCalculator(aapsLogger,rh,activePlugin,profileFunction,dateUtil,iobCobCalculator, repository)
        this.mealData.put("TDDAIMI3", tddCalculator.averageTDD(tddCalculator.calculate(3))?.totalAmount)
        this.mealData.put("TDDAIMIBASAL3", tddCalculator.averageTDD(tddCalculator.calculate(3))?.basalAmount)
        this.mealData.put("TDDAIMIBASAL7", tddCalculator.averageTDD(tddCalculator.calculate(7))?.basalAmount)

        //StatTIR = TirCalculator(rh,profileFunction,dateUtil,repository)
        this.mealData.put("StatLow7", tirCalculator.averageTIR(tirCalculator.calculate(7, 65.0, 180.0))?.belowPct())
        this.mealData.put("currentTIRLow", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.belowPct())
        this.mealData.put("currentTIRRange", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.inRangePct())
        this.mealData.put("currentTIRAbove", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.abovePct())

       if (sp.getBoolean(R.string.key_openapsama_use_autosens, false) && tdd7D != null && tdd7D != 0.0)
            autosensData.put("ratio", aimisensitivity)
        else
            autosensData.put("ratio", 1.0)

        this.microBolusAllowed = microBolusAllowed
        smbAlwaysAllowed = advancedFiltering
        currentTime = now
        this.flatBGsDetected = flatBGsDetected
    }

    private fun makeParam(jsonObject: JSONObject?, rhino: Context, scope: Scriptable): Any {
        return if (jsonObject == null) Undefined.instance
        else NativeJSON.parse(rhino, scope, jsonObject.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun makeParamArray(jsonArray: JSONArray?, rhino: Context, scope: Scriptable): Any {
        return NativeJSON.parse(rhino, scope, jsonArray.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    @Throws(IOException::class) private fun readFile(filename: String): String {
        val bytes = scriptReader.readFile(filename)
        var string = String(bytes, StandardCharsets.UTF_8)
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20)
        }
        return string
    }

    init {
        injector.androidInjector().inject(this)
    }
}