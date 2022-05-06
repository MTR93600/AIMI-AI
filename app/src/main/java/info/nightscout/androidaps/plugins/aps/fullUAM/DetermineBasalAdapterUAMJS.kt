package info.nightscout.androidaps.plugins.aps.fullUAM

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.getPassedDurationToTimeInMinutes
import info.nightscout.androidaps.extensions.plannedRemainingMinutes
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.utils.stats.TddCalculator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import info.nightscout.androidaps.utils.stats.TirCalculator


//import info.nightscout.androidaps.dana.DanaPump



class DetermineBasalAdapterUAMJS internal constructor(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector): DetermineBasalAdapterInterface {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    //@Inject lateinit var danaPump: DanaPump


    private var profile = JSONObject()
    private var mGlucoseStatus = JSONObject()
    private var iobData: JSONArray? = null
    private var mealData = JSONObject()
    private var currentTemp = JSONObject()
    private var autosensData = JSONObject()
    private var microBolusAllowed = false
    private var smbAlwaysAllowed = false
    private var currentTime: Long = 0
    private var saveCgmSource = false
    private var lastBolusNormalTime: Long = 0
    private var lastBolusNormalTimeValue: Long = 0
    private var AIMI_lastCarbUnit: Long = 0
    private var AIMI_lastCarbTime: Long = 0
    private val millsToThePast = T.hours(4).msecs()
    private val millsToThePastSMB = T.hours(1).msecs()
    private var tddAIMI: TddCalculator? = null
    private var StatTIR: TirCalculator? = null

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""



    @Suppress("SpellCheckingInspection")
    override operator fun invoke(): APSResult? {
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
        aapsLogger.debug(LTag.APS, "isSaveCgmSource: $saveCgmSource")
        var determineBasalResultUAM: DetermineBasalResultUAM? = null
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
            rhino.evaluateString(scope, readFile("FullUAM/determine-basal.js"), "JavaScript", 0, null)
            rhino.evaluateString(scope, readFile("FullUAM/basal-set-temp.js"), "setTempBasal.js", 0, null)
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
                    java.lang.Boolean.valueOf(saveCgmSource)
                )
                val jsResult = determineBasalObj.call(rhino, scope, scope, params) as NativeObject
                scriptDebug = LoggerCallback.scriptDebug

                // Parse the jsResult object to a JSON-String
                val result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString()
                aapsLogger.debug(LTag.APS, "Result: $result")
                try {
                    val resultJson = JSONObject(result)
                    determineBasalResultUAM = DetermineBasalResultUAM(injector, resultJson)
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
        return determineBasalResultUAM
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
    isSaveCgmSource: Boolean
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

        //mProfile.put("high_temptarget_raises_sensitivity", SP.getBoolean(R.string.key_high_temptarget_raises_sensitivity, UAMDefaults.high_temptarget_raises_sensitivity));
//**********************************************************************************************************************************************
        //this.profile.put("high_temptarget_raises_sensitivity", false)
        //mProfile.put("low_temptarget_lowers_sensitivity", SP.getBoolean(R.string.key_low_temptarget_lowers_sensitivity, UAMDefaults.low_temptarget_lowers_sensitivity));
        this.profile.put("high_temptarget_raises_sensitivity",sp.getBoolean(rh.gs(R.string.key_high_temptarget_raises_sensitivity),UAMDefaults.high_temptarget_raises_sensitivity))
        this.profile.put("low_temptarget_lowers_sensitivity",sp.getBoolean(rh.gs(R.string.key_low_temptarget_lowers_sensitivity),UAMDefaults.low_temptarget_lowers_sensitivity))
        //this.profile.put("low_temptarget_lowers_sensitivity", false)
//**********************************************************************************************************************************************
        this.profile.put("sensitivity_raises_target", sp.getBoolean(R.string.key_sensitivity_raises_target, UAMDefaults.sensitivity_raises_target))
        this.profile.put("resistance_lowers_target", sp.getBoolean(R.string.key_resistance_lowers_target, UAMDefaults.resistance_lowers_target))
        this.profile.put("adv_target_adjustments", UAMDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", UAMDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", UAMDefaults.half_basal_exercise_target)
        this.profile.put("maxCOB", UAMDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, UAMDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", UAMDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)

        this.profile.put("A52_risk_enable", UAMDefaults.A52_risk_enable)
        val smbEnabled = sp.getBoolean(R.string.key_use_smb, false)
        this.profile.put("SMBInterval", sp.getInt(R.string.key_smbinterval, UAMDefaults.SMBInterval))
        this.profile.put("enableSMB_with_COB", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_COB, false))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_temptarget, false))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && sp.getBoolean(R.string.key_allowSMB_with_high_temptarget, false))
        this.profile.put("enableSMB_always", smbEnabled && sp.getBoolean(R.string.key_enableSMB_always, false) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && sp.getBoolean(R.string.key_enableSMB_after_carbs, false) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", sp.getInt(R.string.key_smbmaxminutes, UAMDefaults.maxSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uamsmbmaxminutes, UAMDefaults.maxUAMSMBBasalMinutes))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", sp.getInt(R.string.key_carbsReqThreshold, UAMDefaults.carbsReqThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")))
//**********************************************************************************************************************************************
        //this.profile.put("UAM_InsulinReq",SafeParse.stringToDouble(sp.getString(R.string.key_UAM_InsulinReq,"65")))
        this.profile.put("iTime",SafeParse.stringToDouble(sp.getString(R.string.key_iTime,"180")))
        this.profile.put("iTime_MaxBolus_minutes",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_MaxBolus_minutes,"200")))
        this.profile.put("iTime_Bolus",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Bolus,"2")))
        this.profile.put("iTime_Start_Bolus",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Starting_Bolus,"2")))
        this.profile.put("smb_delivery_ratio", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio, "0.5")))
        this.profile.put("smb_delivery_ratio_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_min, "0.5")))
        this.profile.put("smb_delivery_ratio_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_max, "0.9")))
        this.profile.put("smb_delivery_ratio_bg_range", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_delivery_ratio_bg_range, "40")))
        this.profile.put("smb_max_range_extension", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_smb_max_range_extension, "1.2")))
        this.profile.put("enable_AIMI_UAM", sp.getBoolean(R.string.key_use_AimiUAM, false))
        this.profile.put("enable_AIMI_Power", sp.getBoolean(R.string.key_use_AimiPower, false))
        this.profile.put("enable_AIMI_UAM_U200", sp.getBoolean(R.string.key_use_LuymjevU200, false))
        this.profile.put("enable_AIMI_UAM_U100", sp.getBoolean(R.string.key_use_LuymjevU100, false))
        this.profile.put("enable_AIMI_UAM_Fiasp", sp.getBoolean(R.string.key_use_Fiasp, false))
        this.profile.put("enable_AIMI_UAM_Novorapid", sp.getBoolean(R.string.key_use_Novorapid, false))
        this.profile.put("key_use_AimiUAM_ISF", sp.getBoolean(R.string.key_use_AimiUAM_ISF, false))
        this.profile.put("key_use_AIMI_BreakFastLight", sp.getBoolean(R.string.key_use_AIMI_BreakFastLight, false))
        this.profile.put("key_AIMI_BreakFastLight_timestart", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timestart, "6")))
        this.profile.put("key_AIMI_BreakFastLight_timeend", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timeend, "10")))
        this.profile.put("key_use_AIMI_CAP", SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "3")))
        this.profile.put("key_insulin_oref_peak", SafeParse.stringToDouble(sp.getString(R.string.key_insulin_oref_peak, "35")))


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
        //bolusMealLinks(now)?.forEach { bolus -> if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.timestamp > lastBolusNormalTime ) lastBolusNormalTime = bolus.timestamp }
        //bolusMealLinks(now)?.forEach { bolus -> if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.timestamp > lastBolusNormalTime) lastBolusNormalTimeValue = bolus.amount.toLong() }


        mGlucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        mGlucoseStatus.put("date", glucoseStatus.date)
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("mealCOB", mealData.mealCOB)
        this.mealData.put("slopeFromMaxDeviation", mealData.slopeFromMaxDeviation)
        this.mealData.put("slopeFromMinDeviation", mealData.slopeFromMinDeviation)
        this.mealData.put("lastBolusTime", mealData.lastBolusTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)

        bolusMealLinks(now)?.forEach { bolus -> if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.timestamp > lastBolusNormalTime ) lastBolusNormalTime = bolus.timestamp }
        //bolusMealLinks(now)?.forEach { bolus -> if (bolus.type == Bolus.Type.NORMAL && bolus.isValid ) lastBolusNormalTimeValue = bolus.amount.toLong() }

        this.mealData.put("lastBolusNormalTime", lastBolusNormalTime)
        //this.mealData.put("lastBolusNormalTimeValue",lastBolusNormalTimeValue)

        // get the last bolus time of a manual bolus for EN activation
        val getlastBolusNormal = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL).blockingGet()
        val lastBolusNormalUnits = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.amount else 0L
        this.mealData.put("lastBolusNormalUnits", lastBolusNormalUnits)
        CarbsMealLinks(now)?.forEach { Carbs -> if (Carbs.isValid && Carbs.timestamp > AIMI_lastCarbTime ) AIMI_lastCarbTime = Carbs.timestamp }
        CarbsMealLinks(now)?.forEach { Carbs -> if (Carbs.isValid && Carbs.amount > 30 ) AIMI_lastCarbUnit = Carbs.amount.toLong() }
        this.mealData.put("AIMI_lastCarbTime", AIMI_lastCarbTime)
        this.mealData.put("AIMI_lastCarbUnit", AIMI_lastCarbUnit)



        val getlastBolusSMB = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.SMB).blockingGet()
        val lastBolusSMBUnits = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.amount else 0L
        val lastBolusSMBTime = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.timestamp else 0L
        this.mealData.put("lastBolusSMBUnits", lastBolusSMBUnits)
        this.mealData.put("lastBolusSMBTime", lastBolusSMBTime)
        // get the last carb time for EN activation


        /*val getlastCarbs = repository.getLastCarbsRecordWrapped().blockingGet()
        val lastCarbTime = if (getlastCarbs is ValueWrapper.Existing) getlastCarbs.value.timestamp else 0L
        val lastCarbUnits = if (getlastCarbs is ValueWrapper.Existing) getlastCarbs.value.amount else 0L
        this.mealData.put("lastNormalCarbTime", lastCarbTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)
        this.mealData.put("lastCarbUnits", lastCarbUnits)*/



        tddAIMI = TddCalculator(aapsLogger,rh,activePlugin,profileFunction,dateUtil,iobCobCalculator, repository)
        this.mealData.put("TDDAIMI3", tddAIMI!!.averageTDD(tddAIMI!!.calculate(3))?.totalAmount)
        this.mealData.put("TDDAIMIBASAL3", tddAIMI!!.averageTDD(tddAIMI!!.calculate(3))?.basalAmount)
        this.mealData.put("TDDAIMIBASAL7", tddAIMI!!.averageTDD(tddAIMI!!.calculate(7))?.basalAmount)
        this.mealData.put("TDDPUMP", tddAIMI!!.calculateDaily().totalAmount)
        //this.mealData.put("TDDLast24", tddAIMI!!.calculate24Daily().totalAmount)
        this.mealData.put("TDDLast8", tddAIMI!!.calculate8Daily().totalAmount)
        //this.mealData.put("TDDPUMP", danaPump.dailyTotalUnits)
        StatTIR = TirCalculator(rh,profileFunction,dateUtil,repository)
        this.mealData.put("StatLow7", StatTIR!!.averageTIR(StatTIR!!.calculate(7, 65.0, 180.0)).belowPct())
        this.mealData.put("StatInRange7", StatTIR!!.averageTIR(StatTIR!!.calculate(7, 65.0, 180.0)).inRangePct())
        this.mealData.put("StatAbove7", StatTIR!!.averageTIR(StatTIR!!.calculate(7, 65.0, 180.0)).abovePct())
        this.mealData.put("currentTIRLow", StatTIR!!.averageTIR(StatTIR!!.calculateDaily(65.0, 180.0)).belowPct())
        this.mealData.put("currentTIRRange", StatTIR!!.averageTIR(StatTIR!!.calculateDaily(65.0, 180.0)).inRangePct())
        this.mealData.put("currentTIRAbove", StatTIR!!.averageTIR(StatTIR!!.calculateDaily(65.0, 180.0)).abovePct())
        this.mealData.put("currentTIR_70_140_Above", StatTIR!!.averageTIR(StatTIR!!.calculateDaily(70.0, 140.0)).abovePct())
        //this.mealData.put("TDDPUMP1", danaPump.dailyTotalUnits)
        //this.mealData.put("TDDPUMP2", pumpHistory.tddHistory.get(DEFAULT_BUFFER_SIZE))



        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }
        this.microBolusAllowed = microBolusAllowed
        smbAlwaysAllowed = advancedFiltering
        currentTime = now
        saveCgmSource = isSaveCgmSource
    }

    private fun makeParam(jsonObject: JSONObject?, rhino: Context, scope: Scriptable): Any {
        return if (jsonObject == null) Undefined.instance
        else NativeJSON.parse(rhino, scope, jsonObject.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun makeParamArray(jsonArray: JSONArray?, rhino: Context, scope: Scriptable): Any {
        return NativeJSON.parse(rhino, scope, jsonArray.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun bolusMealLinks(now: Long) = repository.getBolusesDataFromTime(now - millsToThePast, false).blockingGet()
    private fun CarbsMealLinks(now: Long) = repository.getCarbsDataFromTime(now - millsToThePast, false).blockingGet()
    //private fun bolusMealLinksSMB(now: Long) = repository.getBolusesDataFromTime(now - millsToThePastSMB, false).blockingGet()



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