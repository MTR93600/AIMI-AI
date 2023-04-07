package info.nightscout.plugins.aps.aimi

import android.os.Environment
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.getPassedDurationToTimeInMinutes
import info.nightscout.core.extensions.plannedRemainingMinutes
import info.nightscout.database.ValueWrapper
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.plugins.aps.APSResultObject
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
import info.nightscout.database.entities.UserEntry
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.plugins.aps.loop.LoopVariantPreference
import info.nightscout.plugins.aps.openAPSaiSMB.DetermineBasalResultaiSMB
import java.io.File
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt
import org.tensorflow.lite.Interpreter

class DetermineBasalAdapterAIMIJS internal constructor(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector): DetermineBasalAdapter {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator



    private var iob = 0.0f
    private var cob = 0.0f
    private var lastCarbAgeMin: Int = 0
    private var futureCarbs = 0.0f
    private var recentNotes: List<UserEntry>? = null
    private var tags0to60minAgo = ""
    private var tags60to120minAgo = ""
    private var tags120to180minAgo = ""
    private var tags180to240minAgo = ""
    private var bg = 0.0f
    private var targetBg = 100.0f
    private var delta = 0.0f
    private var shortAvgDelta = 0.0f
    private var longAvgDelta = 0.0f
    private var accelerating_up: Int = 0
    private var deccelerating_up: Int = 0
    private var accelerating_down: Int = 0
    private var deccelerating_down: Int = 0
    private var stable: Int = 0
    private var maxIob = 0.0f
    private var maxSMB = 1.0f
    private var tdd7DaysPerHour = 0.0f
    private var tdd2DaysPerHour = 0.0f
    private var tddPerHour = 0.0f
    private var tdd24HrsPerHour = 0.0f
    private var tddlastHrs = 0.0f
    private var hourOfDay: Int = 0
    private var weekend: Int = 0
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
    private val millsToThePast = T.mins(60).msecs()
    private val millsToThePast2 = T.mins(40).msecs()
    private val millsToThePast3 = T.mins(180).msecs()
    private var lastBolusNormalTimecount: Long = 0
    private var lastPBoluscount: Long = 0
    private var extendedsmbCount: Long = 0
    private var lastBolusSMBcount: Long = 0
    private var SMBcount: Long = 0
    private var MaxSMBcount: Long = 0
    private var b30bolus: Long = 0
    private var lastBolusNormalUnits = 0.0f
    private var recentSteps5Minutes: Int = 0
    private var recentSteps10Minutes: Int = 0
    private var recentSteps15Minutes: Int = 0
    private var recentSteps30Minutes: Int = 0
    private var recentSteps60Minutes: Int = 0
    private val path = File(Environment.getExternalStorageDirectory().toString())
    private val modelFile = File(path, "AAPS/ml/model.tflite")
    private var now: Long = 0

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""



    @Suppress("SpellCheckingInspection")

    private fun logDataToCsv(predictedSMB: Float, smbToGive: Float) {
        val dateStr = dateUtil.dateAndTimeString(dateUtil.now())

        val headerRow = "dateStr,dateLong,hourOfDay,weekend," +
            "bg,targetBg,iob,cob,lastCarbAgeMin,futureCarbs,delta,shortAvgDelta,longAvgDelta," +
            "tdd7DaysPerHour,tdd2DaysPerHour,tddPerHour,tdd24HrsPerHour," +
            "recentSteps5Minutes,recentSteps10Minutes,recentSteps15Minutes,recentSteps30Minutes,recentSteps60Minutes," +
            "tags0to60minAgo,tags60to120minAgo,tags120to180minAgo,tags180to240minAgo," +
            "predictedSMB,maxIob,maxSMB,smbGiven\n"
        val valuesToRecord = "$dateStr,${dateUtil.now()},$hourOfDay,$weekend," +
            "$bg,$targetBg,$iob,$cob,$lastCarbAgeMin,$futureCarbs,$delta,$shortAvgDelta,$longAvgDelta," +
            "$tdd7DaysPerHour,$tdd2DaysPerHour,$tddPerHour,$tdd24HrsPerHour," +
            "$recentSteps5Minutes,$recentSteps10Minutes,$recentSteps15Minutes,$recentSteps30Minutes,$recentSteps60Minutes," +
            "$tags0to60minAgo,$tags60to120minAgo,$tags120to180minAgo,$tags180to240minAgo," +
            "$predictedSMB,$maxIob,$maxSMB,$smbToGive"

        val file = File(path, "AAPS/aiSMB_records.csv")
        if (!file.exists()) {
            file.createNewFile()
            file.appendText(headerRow)
        }
        file.appendText(valuesToRecord + "\n")
    }
    private fun applySafetyPrecautions(smbToGiveParam: Float): Float {
        var smbToGive = smbToGiveParam
        // don't exceed max IOB
        if (iob + smbToGive > maxIob) {
            smbToGive = maxIob - iob
        }
        // don't exceed max SMB
        if (smbToGive > maxSMB) {
            smbToGive = maxSMB
        }
        // don't give insulin if below target too aggressive
        val belowTargetAndDropping = bg < targetBg && delta < -2
        val belowTargetAndStableButNoCob = bg < targetBg - 15 && shortAvgDelta <= 2 && cob <= 5
        val belowMinThreshold = bg < 70
        if (belowTargetAndDropping || belowMinThreshold || belowTargetAndStableButNoCob) {
            smbToGive = 0.0f
        }

        // don't give insulin if dropping fast
        val droppingFast = bg < 150 && delta < -5
        val droppingFastAtHigh = bg < 200 && delta < -7
        val droppingVeryFast = delta < -10
        if (droppingFast || droppingFastAtHigh || droppingVeryFast) {
            smbToGive = 0.0f
        }
        if (smbToGive < 0.0f) {
            smbToGive = 0.0f
        }
        return smbToGive
    }

    private fun roundToPoint05(number: Float): Float {
        return (number * 20.0).roundToInt() / 20.0f
    }

    private fun roundToPoint001(number: Float): Float {
        return (number * 1000.0).roundToInt() / 1000.0f
    }

    private fun calculateSMBFromModel(): Float {
        if (!modelFile.exists()) {
            aapsLogger.error(LTag.APS, "NO Model found at AAPS/ml/model.tflite")
            return 0.0f
        }

        val interpreter = Interpreter(modelFile)
        val modelInputs = floatArrayOf(
            hourOfDay.toFloat(), weekend.toFloat(),
            bg, targetBg, iob, cob, lastCarbAgeMin.toFloat(), futureCarbs, delta, shortAvgDelta, longAvgDelta,
            tdd7DaysPerHour, tdd2DaysPerHour, tddPerHour, tdd24HrsPerHour,
            recentSteps5Minutes.toFloat(), recentSteps10Minutes.toFloat(), recentSteps15Minutes.toFloat(), recentSteps30Minutes.toFloat(), recentSteps60Minutes.toFloat()
        )
        val output = arrayOf(floatArrayOf(0.0f))
        interpreter.run(modelInputs, output)
        interpreter.close()
        var smbToGive = output[0][0]
        smbToGive = "%.4f".format(smbToGive.toDouble()).toFloat()
        return smbToGive
    }

    override operator fun invoke(): DetermineBasalResultSMB? {

        val predictedSMB = calculateSMBFromModel()
        var smbToGive = predictedSMB


        smbToGive = roundToPoint05(smbToGive)

        //logDataToCsv(predictedSMB, smbToGive)
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
            rhino.evaluateString(scope, readFile(LoopVariantPreference.getVariantFileName(sp, AIMIDefaults.folderName)), "JavaScript", 0, null)
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
                    logDataToCsv(determineBasalResultSMB.smb.toFloat(),determineBasalResultSMB.smb.toFloat())
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
        this.now = System.currentTimeMillis()
        this.hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        this.weekend = if (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY) 1 else 0

        val iobCalcs = iobCobCalculator.calculateIobFromBolus()
        this.iob = iobCalcs.iob.toFloat() + iobCalcs.basaliob.toFloat()
        this.bg = glucoseStatus.glucose.toFloat()
        this.targetBg = targetBg.toFloat()
        this.cob = mealData.mealCOB.toFloat()
        var lastCarbTimestamp = mealData.lastCarbTime

        if(lastCarbTimestamp.toInt() == 0) {
            val oneDayAgoIfNotFound = now - 24 * 60 * 60 * 1000
            lastCarbTimestamp = iobCobCalculator.getMostRecentCarbByDate() ?: oneDayAgoIfNotFound
        }
        this.lastCarbAgeMin = ((now - lastCarbTimestamp) / (60 * 1000)).toDouble().roundToInt()

        if(lastCarbAgeMin < 15 && cob == 0.0f) {
            this.cob = iobCobCalculator.getMostRecentCarbAmount()?.toFloat() ?: 0.0f
        }

        this.futureCarbs = iobCobCalculator.getFutureCob().toFloat()
        val fourHoursAgo = now - 4 * 60 * 60 * 1000
        this.recentNotes = iobCobCalculator.getUserEntryDataWithNotesFromTime(fourHoursAgo)
        this.tags0to60minAgo = parseNotes(0, 60)
        this.tags60to120minAgo = parseNotes(60, 120)
        this.tags120to180minAgo = parseNotes(120, 180)
        this.tags180to240minAgo = parseNotes(180, 240)

        this.delta = glucoseStatus.delta.toFloat()
        this.shortAvgDelta = glucoseStatus.shortAvgDelta.toFloat()
        this.longAvgDelta = glucoseStatus.longAvgDelta.toFloat()

        this.accelerating_up = if (delta > 2 && delta - longAvgDelta > 2) 1 else 0
        this.deccelerating_up = if (delta > 0 && (delta < shortAvgDelta || delta < longAvgDelta)) 1 else 0
        this.accelerating_down = if (delta < -2 && delta - longAvgDelta < -2) 1 else 0
        this.deccelerating_down = if (delta < 0 && (delta > shortAvgDelta || delta > longAvgDelta)) 1 else 0
        this.stable = if (delta>-3 && delta<3 && shortAvgDelta>-3 && shortAvgDelta<3 && longAvgDelta>-3 && longAvgDelta<3) 1 else 0

        val tdd7Days = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        this.tdd7DaysPerHour = tdd7Days / 24

        val tdd2Days = tddCalculator.averageTDD(tddCalculator.calculate(2, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        this.tdd2DaysPerHour = tdd2Days / 24

        val tddDaily = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.totalAmount?.toFloat() ?: 0.0f
        this.tddPerHour = tddDaily / 24

        val tdd24Hrs = tddCalculator.calculateDaily(-24, 0)?.totalAmount?.toFloat() ?: 0.0f
        this.tdd24HrsPerHour = tdd24Hrs / 24

        tddlastHrs = tddCalculator.calculateDaily(-1,0)?.totalAmount?.toFloat() ?: 0.0f

        this.maxIob = sp.getDouble(R.string.key_openapssmb_max_iob, 5.0).toFloat()
        this.maxSMB = (sp.getDouble(R.string.key_use_AIMI_CAP, 150.0).toFloat() * basalRate / 100).toFloat()

        // profile.dia
        val abs = iobCobCalculator.calculateAbsoluteIobFromBaseBasals(System.currentTimeMillis())
        val absIob = abs.iob
        val absNet = abs.netInsulin
        val absBasal = abs.basaliob

        aapsLogger.debug(LTag.APS, "IOB options : bolus iob: ${iobCalcs.iob} basal iob : ${iobCalcs.basaliob}")
        aapsLogger.debug(LTag.APS, "IOB options : calculateAbsoluteIobFromBaseBasals iob: $absIob net : $absNet basal : $absBasal")



        val pump = activePlugin.activePump
        val pumpBolusStep = pump.pumpDescription.bolusStep
        this.profile = JSONObject()
        this.profile.put("max_iob", maxIob)
        this.profile.put("dia", min(profile.dia, 3.0))
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
        this.profile.put("skip_neutral_temps", true)
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_adjust_targets", sp.getBoolean(R.string.key_openapsama_autosens_adjusttargets, true))
        this.profile.put("accelerating_up",accelerating_up)
        this.profile.put("deccelerating_up",deccelerating_up)
        this.profile.put("accelerating_down",accelerating_down)
        this.profile.put("deccelerating_down",deccelerating_down)
        this.profile.put("stable",stable)
        this.profile.put("tdd7DaysPerHour",tdd7DaysPerHour)
        this.profile.put("tdd2DaysPerHour",tdd2DaysPerHour)
        this.profile.put("tddPerHour",tddPerHour)
        this.profile.put("tdd24HrsPerHour",tdd24HrsPerHour)
        this.profile.put("mss",maxSMB)
        this.profile.put("tddlastHrs",tddlastHrs)

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
        this.profile.put("b30_bolus",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_bolus,"50")))
        this.profile.put("b30_bolus_duration",SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_bolus_duration,"50")))
        this.profile.put("enable_AIMI_b30_bolus", sp.getBoolean(R.string.key_use_Aimib30bolus, false))
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
        this.profile.put("key_use_AimiUAM_ISF", sp.getBoolean(R.string.key_use_AimiUAM_ISF, false))
        this.profile.put("key_use_AIMI_BreakFastLight", sp.getBoolean(R.string.key_use_AIMI_BreakFastLight, false))
        this.profile.put("key_use_disable_b30_BFL", sp.getBoolean(R.string.key_use_disable_b30_BFL, false))
        this.profile.put("key_AIMI_BreakFastLight_timestart", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timestart, "6")))
        this.profile.put("key_AIMI_BreakFastLight_timeend", SafeParse.stringToDouble(sp.getString(R.string.key_AIMI_BreakFastLight_timeend, "10")))
        this.profile.put("key_use_AIMI_CAP", SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "150")))
        this.profile.put("key_use_newsmb", sp.getBoolean(R.string.key_use_newSMB, false))
        this.profile.put("key_use_enable_mssv", sp.getBoolean(R.string.key_use_enable_mssv, false))
        this.profile.put("key_use_countsteps", sp.getBoolean(R.string.key_use_countsteps, false))

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
        lastBolusNormalUnits = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.amount.toFloat() else 0.0f
        val lastBolusNormalTime = if (getlastBolusNormal is ValueWrapper.Existing) getlastBolusNormal.value.timestamp else 0L
        this.mealData.put("lastBolusNormalUnits", lastBolusNormalUnits)
        this.mealData.put("lastBolusNormalTime", lastBolusNormalTime)

        val bolusMealLinks = repository.getBolusesDataFromTime(now - millsToThePast, false).blockingGet()
        bolusMealLinks.forEach { bolus ->
            if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.amount >= SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Starting_Bolus, "2"))) lastBolusNormalTimecount += 1
            if (bolus.type == Bolus.Type.SMB && bolus.isValid) lastBolusSMBcount += 1
        }
        val extendedsmb = repository.getBolusesDataFromTime(now - millsToThePast2,false).blockingGet()
        extendedsmb.forEach{bolus ->
            if (bolus.type == Bolus.Type.SMB && bolus.isValid && bolus.amount.toLong() == lastBolusNormalUnits.toLong()) extendedsmbCount +=1
            if ((bolus.type == Bolus.Type.SMB) && bolus.isValid && bolus.amount.toLong() !== lastBolusNormalUnits.toLong()) SMBcount += 1
            if (bolus.type == Bolus.Type.SMB && bolus.isValid && bolus.amount >= (0.8 * (profile.getBasal() * SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "150"))/100)) && sp.getBoolean(R.string.key_use_newSMB, false) === true ) MaxSMBcount += 1
            if (bolus.type == Bolus.Type.SMB && bolus.isValid && bolus.amount === (lastBolusNormalUnits * (profile.getBasal() * SafeParse.stringToDouble(sp.getString(R.string.key_iTime_B30_bolus, "50"))/100)) && sp.getBoolean(R.string.key_use_newSMB, false) === true && sp.getBoolean(R.string.key_use_Aimib30bolus, false) === true) b30bolus += 1
        }
        val lastprebolus = repository.getBolusesDataFromTime(now - millsToThePast3,false).blockingGet()
        lastprebolus.forEach { bolus ->
            if (bolus.type == Bolus.Type.NORMAL && bolus.isValid && bolus.amount >= SafeParse.stringToDouble(sp.getString(R.string.key_iTime_Starting_Bolus, "2"))) lastPBoluscount += 1
        }

        this.mealData.put("countBolus", lastBolusNormalTimecount)
        this.mealData.put("countSMB", lastBolusSMBcount)
        this.mealData.put("countSMB40", SMBcount)
        this.mealData.put("extendedsmbCount", extendedsmbCount)
        this.mealData.put("MaxSMBcount", MaxSMBcount)
        this.mealData.put("b30bolus", b30bolus)

        val getlastBolusSMB = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.SMB).blockingGet()
        val lastBolusSMBUnits = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.amount else 0L
        val lastBolusSMBTime = if (getlastBolusSMB is ValueWrapper.Existing) getlastBolusSMB.value.timestamp else 0L
        this.mealData.put("lastBolusSMBUnits", lastBolusSMBUnits)
        this.mealData.put("lastBolusSMBTime", lastBolusSMBTime)

        this.recentSteps5Minutes = StepService.getRecentStepCount5Min()
        this.recentSteps10Minutes = StepService.getRecentStepCount10Min()
        this.recentSteps15Minutes = StepService.getRecentStepCount15Min()
        this.recentSteps30Minutes = StepService.getRecentStepCount30Min()
        this.recentSteps60Minutes = StepService.getRecentStepCount60Min()

        val lastHourTIRAbove = tirCalculator.averageTIR(tirCalculator.calculateHour(72.0, 160.0))?.abovePct()
        val last2HourTIRAbove = tirCalculator.averageTIR(tirCalculator.calculate2Hour(72.0, 160.0))?.abovePct()
        val lastHourTIRLow = tirCalculator.averageTIR(tirCalculator.calculateHour(72.0, 160.0))?.belowPct()
        val tdd1D = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = true))?.totalAmount
        var tdd7D = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = true))?.totalAmount
        val tddLast24H = tddCalculator.calculateDaily(-24, 0)?.totalAmount
        val tddLast4H = tddCalculator.calculateDaily(-4, 0)?.totalAmount
        val tddLast8to4H = tddCalculator.calculateDaily(-8, -4)?.totalAmount
        val TDDLast8 = tddCalculator.calculateDaily(-8, 0)?.totalAmount
        val TDD4hwindow = tddCalculator.calculateDaily(-24, -21)?.totalAmount
        val insulinR = TDD4hwindow?.div(3)
        val maxaimismb = SafeParse.stringToDouble(sp.getString(R.string.key_use_AIMI_CAP, "150"))


        val insulinDivisor = when {
            insulin.peak >= 35 -> 55 // lyumjev peak: 45
            insulin.peak > 45 -> 65 // ultra rapid peak: 55
            else              -> 75 // rapid peak: 75
        }

// Add variable for readability and to avoid repeating the same calculations
        val tddWeightedFromLast8H: Double? = if(tddLast4H != null && tddLast8to4H != null){
            ((1.4 * tddLast4H) + (0.6 * tddLast8to4H)) * 3
        }else{
            null
        }

// Use null-safe operator to avoid unnecessary unwrapping
        tdd7D = tdd7D?.let { it } ?: return

// Use when expression to make the code more readable
        val tdd = when {
            // Checking the condition if its true
            tddWeightedFromLast8H != null && tdd1D != null && tdd7D != 0.0 && lastHourTIRLow!! > 0 -> ((tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)) * 0.85
            tddWeightedFromLast8H != null && tdd1D != null && tdd7D != 0.0 && lastHourTIRAbove!! > 0 -> ((tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)) * 1.15
            tddWeightedFromLast8H != null && tdd1D != null && tdd7D != 0.0 -> (tddWeightedFromLast8H * 0.33) + (tdd7D * 0.34) + (tdd1D * 0.33)
            else -> {
                tddWeightedFromLast8H ?: 0.0 // or any default value you want
            }
        }

        val aimisensitivity = tddLast24H?.let { tddLast24H / tdd7D } ?: return

// Add comments to explain the purpose and logic of the code
        var variableSensitivity: Double?
        if (tddLast24H != null && tddLast4H != null && tddLast8to4H != null) {
            //Calculating aimisensitivity
            this.profile.put("aimisensitivity", aimisensitivity)
            //Calculating variableSensitivity
            variableSensitivity = 1800 / (tdd * (ln((glucoseStatus.glucose / insulinDivisor) + 1)))
            if (recentSteps5Minutes > 100 && recentSteps10Minutes > 200) variableSensitivity *= 1.5
            if (recentSteps30Minutes > 500 && recentSteps5Minutes >= 0 && recentSteps5Minutes < 100 ) variableSensitivity *= 1.3
            //Round to 0.1
            variableSensitivity = Round.roundTo(variableSensitivity, 0.1)
        } else {
            variableSensitivity = null
            // If the above conditions are not met then we are assigning profile's isfMgdl to variableSensitivity
            val aimisensitivity = 1
            this.profile.put("aimisensitivity", aimisensitivity)
        }



        this.profile.put("variable_sens", variableSensitivity)
        this.profile.put("lastHourTIRLow", lastHourTIRLow)
        this.profile.put("TDD", tdd)
        this.profile.put("lastHourTIRAbove", lastHourTIRAbove)
        this.profile.put("last2HourTIRAbove", last2HourTIRAbove)
        this.profile.put("lastPBoluscount", lastPBoluscount)

        this.profile.put("insulinDivisor", insulinDivisor)
        //this.profile.put("tddlastHaverage", tddlastHaverage)
        this.profile.put("key_use_AimiIgnoreCOB", sp.getBoolean(R.string.key_use_AimiIgnoreCOB, false))

        //tddAIMI = TddCalculator(aapsLogger,rh,activePlugin,profileFunction,dateUtil,iobCobCalculator, repository)
        this.mealData.put("TDDAIMI3", tddCalculator.averageTDD(tddCalculator.calculate(3, allowMissingDays = true))?.totalAmount)
        this.mealData.put("TDDAIMIBASAL3", tddCalculator.averageTDD(tddCalculator.calculate(3, allowMissingDays = true))?.basalAmount)
        this.mealData.put("TDDAIMIBASAL7", tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = true))?.basalAmount)
        this.mealData.put("TDDPUMP", tddCalculator.calculateDaily(-8, 0)?.totalAmount)
        this.mealData.put("aimiTDD24", tddCalculator.calculateDaily(-24, 0)?.totalAmount)
        this.mealData.put("TDDLast8", TDDLast8)


        //StatTIR = TirCalculator(rh,profileFunction,dateUtil,repository)
        this.mealData.put("StatLow7", tirCalculator.averageTIR(tirCalculator.calculate(7, 65.0, 180.0))?.belowPct())
        this.mealData.put("StatInRange7", tirCalculator.averageTIR(tirCalculator.calculate(7, 65.0, 180.0))?.inRangePct())
        this.mealData.put("currentTIRLow", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.belowPct())
        this.mealData.put("currentTIRRange", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.inRangePct())
        this.mealData.put("currentTIRAbove", tirCalculator.averageTIR(tirCalculator.calculateDaily(65.0, 180.0))?.abovePct())
        this.mealData.put("currentTIR_70_140_Above", tirCalculator.averageTIR(tirCalculator.calculateDaily(70.0, 140.0))?.abovePct())




        this.profile.put("recentSteps5Minutes", recentSteps5Minutes)
        this.profile.put("recentSteps10Minutes", recentSteps10Minutes)
        this.profile.put("recentSteps15Minutes", recentSteps15Minutes)
        this.profile.put("recentSteps30Minutes", recentSteps30Minutes)
        this.profile.put("recentSteps60Minutes", recentSteps60Minutes)

        this.profile.put("insulinR", insulinR)


        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }

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

    fun parseNotes(startMinAgo: Int, endMinAgo: Int): String {
        val olderTimeStamp = now - endMinAgo * 60 * 1000
        val moreRecentTimeStamp = now - startMinAgo * 60 * 1000
        var notes = ""
        recentNotes?.forEach { note ->
            if(note.timestamp > olderTimeStamp
                && note.timestamp <= moreRecentTimeStamp
                && !note.note.lowercase().contains("low treatment")
                && !note.note.lowercase().contains("less aggressive")
                && !note.note.lowercase().contains("more aggressive")
                && !note.note.lowercase().contains("too aggressive")
            ) {
                notes += if(notes.isEmpty()) "" else " "
                notes += note.note
            }
        }
        notes = notes.lowercase()
        notes.replace(","," ")
        notes.replace("."," ")
        notes.replace("!"," ")
        notes.replace("a"," ")
        notes.replace("an"," ")
        notes.replace("and"," ")
        notes.replace("\\s+"," ")
        return notes
    }

    init {
        injector.androidInjector().inject(this)
    }
}