package info.nightscout.androidaps.plugins.aps.EN

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
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.shared.SafeParse
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
import kotlin.math.roundToInt

class DetermineBasalAdapterENJS internal constructor(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector) {

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
    private var tddAIMI: TddCalculator? = null
    private var StatTIR: TirCalculator? = null

    var currentTempParam: String? = null
        private set
    var iobDataParam: String? = null
        private set
    var glucoseStatusParam: String? = null
        private set
    var profileParam: String? = null
        private set
    var mealDataParam: String? = null
        private set
    var scriptDebug = ""
        private set

    @Suppress("SpellCheckingInspection")
    operator fun invoke(): DetermineBasalResultEN? {
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
        var determineBasalResultEN: DetermineBasalResultEN? = null
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
            rhino.evaluateString(scope, readFile("EN/determine-basal.js"), "JavaScript", 0, null)
            rhino.evaluateString(scope, readFile("EN/basal-set-temp.js"), "setTempBasal.js", 0, null)
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
                    determineBasalResultEN = DetermineBasalResultEN(injector, resultJson)
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
        return determineBasalResultEN
    }

    @Suppress("SpellCheckingInspection") fun setData(profile: Profile,
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
        //mProfile.put("dia", profile.getDia());
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg.roundToInt())
        this.profile.put("max_bg", maxBg.roundToInt())
        this.profile.put("target_bg", targetBg.roundToInt())
        this.profile.put("carb_ratio", profile.getIc())
        this.profile.put("sens", profile.getIsfMgdl())
        this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))

        this.profile.put("high_temptarget_raises_sensitivity", sp.getBoolean(R.string.key_high_temptarget_raises_sensitivity, ENDefaults.high_temptarget_raises_sensitivity))
        // this.profile.put("high_temptarget_raises_sensitivity", false)
        this.profile.put("low_temptarget_lowers_sensitivity", sp.getBoolean(R.string.key_low_temptarget_lowers_sensitivity, ENDefaults.low_temptarget_lowers_sensitivity))
        // this.profile.put("low_temptarget_lowers_sensitivity", false)
        this.profile.put("sensitivity_raises_target", sp.getBoolean(R.string.key_sensitivity_raises_target, ENDefaults.sensitivity_raises_target))
        this.profile.put("resistance_lowers_target", sp.getBoolean(R.string.key_resistance_lowers_target, ENDefaults.resistance_lowers_target))
        this.profile.put("adv_target_adjustments", ENDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", ENDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", ENDefaults.half_basal_exercise_target)
        this.profile.put("maxCOB", ENDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, ENDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", ENDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)
        this.profile.put("A52_risk_enable", ENDefaults.A52_risk_enable)
        val smbEnabled = sp.getBoolean(R.string.key_use_smb, false)
        this.profile.put("SMBInterval", sp.getInt(R.string.key_smbinterval, ENDefaults.SMBInterval))
        this.profile.put("enableSMB_with_COB", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_COB, false))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_temptarget, false))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && sp.getBoolean(R.string.key_allowSMB_with_high_temptarget, false))
        this.profile.put("enableSMB_always", smbEnabled && sp.getBoolean(R.string.key_enableSMB_always, false) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && sp.getBoolean(R.string.key_enableSMB_after_carbs, false) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", sp.getInt(R.string.key_smbmaxminutes, ENDefaults.maxSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uamsmbmaxminutes, ENDefaults.maxUAMSMBBasalMinutes))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", sp.getInt(R.string.key_carbsReqThreshold, ENDefaults.carbsReqThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")))
//**********************************************************************************************************************************************
        // patches ==== START
        this.profile.put("normal_target_bg", profile.getTargetMgdl().roundToInt())

        this.profile.put("enableGhostCOB", sp.getBoolean(R.string.key_use_ghostcob, false))
        this.profile.put("COBinsulinReqPct",SafeParse.stringToDouble(sp.getString(R.string.key_eatingnow_cobinsulinreqpct,"65")))
        this.profile.put("COBBoostWindow", sp.getInt(R.string.key_eatingnow_cobboostminutes, 0))
        this.profile.put("COBBoost_maxBolus", sp.getDouble(R.string.key_eatingnow_cobboost_maxbolus, 0.0))
        //this.profile.put("COBeBGweight",SafeParse.stringToDouble(sp.getString(R.string.key_eatingnow_COBeBGweight,"0")))


        this.profile.put("EatingNowIOBMax", sp.getInt(R.string.key_eatingnow_iobmax, 30))
        this.profile.put("EatingNowTimeStart", sp.getInt(R.string.key_eatingnow_timestart, 9))
        this.profile.put("EatingNowTimeEnd", sp.getInt(R.string.key_eatingnow_timeend, 17))
        this.profile.put("EatingNowinsulinReqPct",SafeParse.stringToDouble(sp.getString(R.string.key_eatingnow_insulinreqpct,"65")))

        this.profile.put("UAMBoost_Bolus_Scale", sp.getDouble(R.string.key_eatingnow_uamboost_bolus_scale, 0.0))
        this.profile.put("UAMBoost_maxBolus", sp.getDouble(R.string.key_eatingnow_uamboost_maxbolus, 0.0))
        this.profile.put("UAMBoost_maxBolus", sp.getDouble(R.string.key_eatingnow_uamboost_maxbolus, 0.0))
        //this.profile.put("UAMeBGweight",SafeParse.stringToDouble(sp.getString(R.string.key_eatingnow_UAMeBGweight,"0")))



        this.profile.put("ISFBoost_maxBolus", sp.getDouble(R.string.key_eatingnow_isfboost_maxbolus, 0.0))
        // this.profile.put("ISF_Max_Scale", sp.getDouble(R.string.key_eatingnow_isf_max_scale, 1.0))
        this.profile.put("SMBbgOffset", Profile.toMgdl(sp.getDouble(R.string.key_eatingnow_smbbgoffset, 0.0),profileFunction.getUnits()))
        this.profile.put("ISFbgOffset", Profile.toMgdl(sp.getDouble(R.string.key_eatingnow_isfbgoffset, 0.0),profileFunction.getUnits()))
        this.profile.put("ISFbgscaler", sp.getDouble(R.string.key_eatingnow_isfbgscaler, 0.0))

        // patches ==== END
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
        // get the last bolus time of a manual bolus for EN activation
        val getlastBolusNormal = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL).blockingGet()
        val lastBolusNormalTime = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.timestamp else 0L
        this.mealData.put("lastBolusNormalTime", lastBolusNormalTime)
        val lastBolusNormalUnits = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.amount else 0L
        this.mealData.put("lastBolusNormalUnits", lastBolusNormalUnits)

        // get the last carb time for EN activation
        val getlastCarbs = repository.getLastCarbsRecordWrapped().blockingGet()
        val lastCarbTime = if (getlastCarbs is ValueWrapper.Existing) getlastCarbs.value.timestamp else 0L
        this.mealData.put("lastNormalCarbTime", lastCarbTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)

        // 3PM is used as a low basal point at which the rest of the day leverages for ISF variance when using one ISF in the profile
        this.profile.put("enableBasalAt3PM", sp.getBoolean(R.string.key_use_3pm_basal, false))
        this.profile.put("BasalAt3PM", profile.getBasal(3600000*15+MidnightTime.calc(now)))

        tddAIMI = TddCalculator(aapsLogger,rh,activePlugin,profileFunction,dateUtil,iobCobCalculator, repository)
        this.mealData.put("TDDLAST24H", tddAIMI!!.calculate24Daily().totalAmount)
        this.mealData.put("TDDAIMI1", tddAIMI!!.averageTDD(tddAIMI!!.calculate(1)).totalAmount)
        this.mealData.put("TDDAIMI3", tddAIMI!!.averageTDD(tddAIMI!!.calculate(3)).totalAmount)
        this.mealData.put("TDDAIMI7", tddAIMI!!.averageTDD(tddAIMI!!.calculate(7)).totalAmount)
        this.mealData.put("TDDPUMP", tddAIMI!!.calculateDaily().totalAmount)
        this.mealData.put("TDDPUMPNOWMS", tddAIMI!!.calculateDaily().totalAmount/(now-MidnightTime.calc(now))*86400000)
        // Override profile ISF with TDD ISF if selected in prefs
        this.profile.put("use_sens_TDD", sp.getBoolean(R.string.key_use_sens_tdd, false))
        this.profile.put("sens_TDD_scale",SafeParse.stringToDouble(sp.getString(R.string.key_sens_tdd_scale,"100")))

        StatTIR = TirCalculator(rh,profileFunction,dateUtil,repository)
        val lowMgdl = 72.0
        val highMgdl = 153.0 // 4.0 - 8.5mmol
        this.mealData.put("TIR7Above",StatTIR!!.averageTIR(StatTIR!!.calculate(7,lowMgdl,highMgdl)).abovePct())
        this.mealData.put("TIR7InRange",StatTIR!!.averageTIR(StatTIR!!.calculate(7,lowMgdl,highMgdl)).inRangePct())
        this.mealData.put("TIR7Below",StatTIR!!.averageTIR(StatTIR!!.calculate(7,lowMgdl,highMgdl)).belowPct())
        this.mealData.put("TIR3Above",StatTIR!!.averageTIR(StatTIR!!.calculate(3,lowMgdl,highMgdl)).abovePct())
        this.mealData.put("TIR3InRange",StatTIR!!.averageTIR(StatTIR!!.calculate(3,lowMgdl,highMgdl)).inRangePct())
        this.mealData.put("TIR3Below",StatTIR!!.averageTIR(StatTIR!!.calculate(3,lowMgdl,highMgdl)).belowPct())
        this.mealData.put("TIR1Above",StatTIR!!.averageTIR(StatTIR!!.calculateDaily(lowMgdl,highMgdl)).abovePct())
        this.mealData.put("TIR1InRange",StatTIR!!.averageTIR(StatTIR!!.calculateDaily(lowMgdl,highMgdl)).inRangePct())
        this.mealData.put("TIR1Below",StatTIR!!.averageTIR(StatTIR!!.calculateDaily(lowMgdl,highMgdl)).belowPct())

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