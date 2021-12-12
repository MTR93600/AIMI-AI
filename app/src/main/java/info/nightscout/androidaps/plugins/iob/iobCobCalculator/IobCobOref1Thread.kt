package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.extensions.target
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Profiler
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class IobCobOref1Thread internal constructor(
    private val injector: HasAndroidInjector,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin, // cannot be injected : HistoryBrowser uses different instance
    private val from: String,
    private val end: Long,
    private val bgDataReload: Boolean,
    private val limitDataToOldestAvailable: Boolean,
    private val cause: Event?
) : Thread() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var context: Context
    @Inject lateinit var sensitivityAAPSPlugin: SensitivityAAPSPlugin
    @Inject lateinit var sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var profiler: Profiler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository

    private var mWakeLock: PowerManager.WakeLock? = null

    init {
        injector.androidInjector().inject(this)
        mWakeLock = (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, rh.gs(R.string.app_name) + ":iobCobThread")
    }

    override fun run() {
        val start = dateUtil.now()
        mWakeLock?.acquire(T.mins(10).msecs())
        try {
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA thread started: $from")
            if (!profileFunction.isProfileValid("IobCobThread")) {
                aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (No profile): $from")
                return  // app still initializing
            }
            //log.debug("Locking calculateSensitivityData");
            val oldestTimeWithData = iobCobCalculatorPlugin.calculateDetectionStart(end, limitDataToOldestAvailable)
            if (bgDataReload) {
                iobCobCalculatorPlugin.ads.loadBgData(end, repository, aapsLogger, dateUtil, rxBus)
                iobCobCalculatorPlugin.clearCache()
            }
            // work on local copy and set back when finished
            val ads = iobCobCalculatorPlugin.ads.clone()
            val bucketedData = ads.bucketedData
            val autosensDataTable = ads.autosensDataTable
            if (bucketedData == null || bucketedData.size < 3) {
                aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (No bucketed data available): $from")
                return
            }
            val prevDataTime = ads.roundUpTime(bucketedData[bucketedData.size - 3].timestamp)
            aapsLogger.debug(LTag.AUTOSENS, "Prev data time: " + dateUtil.dateAndTimeString(prevDataTime))
            var previous = autosensDataTable[prevDataTime]
            // start from oldest to be able sub cob
            for (i in bucketedData.size - 4 downTo 0) {
                val progress = i.toString() + if (buildHelper.isDev()) " ($from)" else ""
                rxBus.send(EventIobCalculationProgress(progress, cause))
                if (iobCobCalculatorPlugin.stopCalculationTrigger) {
                    iobCobCalculatorPlugin.stopCalculationTrigger = false
                    aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (trigger): $from")
                    return
                }
                // check if data already exists
                var bgTime = bucketedData[i].timestamp
                bgTime = ads.roundUpTime(bgTime)
                if (bgTime > ads.roundUpTime(dateUtil.now())) continue
                var existing: AutosensData?
                if (autosensDataTable[bgTime].also { existing = it } != null) {
                    previous = existing
                    continue
                }
                val profile = profileFunction.getProfile(bgTime)
                if (profile == null) {
                    aapsLogger.debug(LTag.AUTOSENS, "Aborting calculation thread (no profile): $from")
                    continue  // profile not set yet
                }
                aapsLogger.debug(LTag.AUTOSENS, "Processing calculation thread: " + from + " (" + i + "/" + bucketedData.size + ")")
                val sens = profile.getIsfMgdl(bgTime)
                val autosensData = AutosensData(injector)
                autosensData.time = bgTime
                if (previous != null) autosensData.activeCarbsList = previous.cloneCarbsList() else autosensData.activeCarbsList = ArrayList()

                //console.error(bgTime , bucketed_data[i].glucose);
                var avgDelta: Double
                var delta: Double
                val bg: Double = bucketedData[i].value
                if (bg < 39 || bucketedData[i + 3].value < 39) {
                    aapsLogger.error("! value < 39")
                    continue
                }
                autosensData.bg = bg
                delta = bg - bucketedData[i + 1].value
                avgDelta = (bg - bucketedData[i + 3].value) / 3
                val iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTemps(bgTime, profile)
                val bgi = -iob.activity * sens * 5
                val deviation = delta - bgi
                val avgDeviation = ((avgDelta - bgi) * 1000).roundToLong() / 1000.0
                var slopeFromMaxDeviation = 0.0
                var slopeFromMinDeviation = 999.0

                // https://github.com/openaps/oref0/blob/master/lib/determine-basal/cob-autosens.js#L169
                if (i < bucketedData.size - 16) { // we need 1h of data to calculate minDeviationSlope
                    @Suppress("UNUSED_VARIABLE") var maxDeviation = 0.0
                    @Suppress("UNUSED_VARIABLE") var minDeviation = 999.0
                    val hourAgo = bgTime + 10 * 1000 - 60 * 60 * 1000L
                    val hourAgoData = ads.getAutosensDataAtTime(hourAgo)
                    if (hourAgoData != null) {
                        val initialIndex = autosensDataTable.indexOfKey(hourAgoData.time)
                        aapsLogger.debug(LTag.AUTOSENS, ">>>>> bucketed_data.size()=" + bucketedData.size + " i=" + i + " hourAgoData=" + hourAgoData.toString())
                        var past = 1
                        try {
                            while (past < 12) {
                                val ad = autosensDataTable.valueAt(initialIndex + past)
                                aapsLogger.debug(LTag.AUTOSENS, ">>>>> past=" + past + " ad=" + ad?.toString())
                                if (ad == null) {
                                    aapsLogger.debug(LTag.AUTOSENS, autosensDataTable.toString())
                                    aapsLogger.debug(LTag.AUTOSENS, bucketedData.toString())
                                    //aapsLogger.debug(LTag.AUTOSENS, iobCobCalculatorPlugin.getBgReadingsDataTable().toString())
                                    val notification = Notification(Notification.SEND_LOGFILES, rh.gs(R.string.sendlogfiles), Notification.LOW)
                                    rxBus.send(EventNewNotification(notification))
                                    sp.putBoolean("log_AUTOSENS", true)
                                    break
                                }
                                // let it here crash on NPE to get more data as i cannot reproduce this bug
                                val deviationSlope = (ad.avgDeviation - avgDeviation) / (ad.time - bgTime) * 1000 * 60 * 5
                                if (ad.avgDeviation > maxDeviation) {
                                    slopeFromMaxDeviation = min(0.0, deviationSlope)
                                    maxDeviation = ad.avgDeviation
                                }
                                if (ad.avgDeviation < minDeviation) {
                                    slopeFromMinDeviation = max(0.0, deviationSlope)
                                    minDeviation = ad.avgDeviation
                                }
                                past++
                            }
                        } catch (e: Exception) {
                            aapsLogger.error("Unhandled exception", e)
                            fabricPrivacy.logException(e)
                            aapsLogger.debug(autosensDataTable.toString())
                            aapsLogger.debug(bucketedData.toString())
                            //aapsLogger.debug(iobCobCalculatorPlugin.getBgReadingsDataTable().toString())
                            val notification = Notification(Notification.SEND_LOGFILES, rh.gs(R.string.sendlogfiles), Notification.LOW)
                            rxBus.send(EventNewNotification(notification))
                            sp.putBoolean("log_AUTOSENS", true)
                            break
                        }
                    } else {
                        aapsLogger.debug(LTag.AUTOSENS, ">>>>> bucketed_data.size()=" + bucketedData.size + " i=" + i + " hourAgoData=" + "null")
                    }
                }
                val recentCarbTreatments = repository.getCarbsDataFromTimeToTimeExpanded(bgTime - T.mins(5).msecs(), bgTime, true).blockingGet()
                for (recentCarbTreatment in recentCarbTreatments) {
                    autosensData.carbsFromBolus += recentCarbTreatment.amount
                    val isAAPSOrWeighted = sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled()
                    autosensData.activeCarbsList.add(autosensData.CarbsInPast(recentCarbTreatment, isAAPSOrWeighted))
                    autosensData.pastSensitivity += "[" + DecimalFormatter.to0Decimal(recentCarbTreatment.amount) + "g]"
                }

                // if we are absorbing carbs
                if (previous != null && previous.cob > 0) {
                    // calculate sum of min carb impact from all active treatments
                    val totalMinCarbsImpact = sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact)

                    // figure out how many carbs that represents
                    // but always assume at least 3mg/dL/5m (default) absorption per active treatment
                    val ci = max(deviation, totalMinCarbsImpact)
                    if (ci != deviation) autosensData.failOverToMinAbsorptionRate = true
                    autosensData.absorbed = ci * profile.getIc(bgTime) / sens
                    // and add that to the running total carbsAbsorbed
                    autosensData.cob = max(previous.cob - autosensData.absorbed, 0.0)
                    autosensData.mealCarbs = previous.mealCarbs
                    autosensData.deductAbsorbedCarbs()
                    autosensData.usedMinCarbsImpact = totalMinCarbsImpact
                    autosensData.absorbing = previous.absorbing
                    autosensData.mealStartCounter = previous.mealStartCounter
                    autosensData.type = previous.type
                    autosensData.uam = previous.uam
                }
                val isAAPSOrWeighted = sensitivityAAPSPlugin.isEnabled() || sensitivityWeightedAveragePlugin.isEnabled()
                autosensData.removeOldCarbs(bgTime, isAAPSOrWeighted)
                autosensData.cob += autosensData.carbsFromBolus
                autosensData.mealCarbs += autosensData.carbsFromBolus
                autosensData.deviation = deviation
                autosensData.bgi = bgi
                autosensData.delta = delta
                autosensData.avgDelta = avgDelta
                autosensData.avgDeviation = avgDeviation
                autosensData.slopeFromMaxDeviation = slopeFromMaxDeviation
                autosensData.slopeFromMinDeviation = slopeFromMinDeviation

                // If mealCOB is zero but all deviations since hitting COB=0 are positive, exclude from autosens
                if (autosensData.cob > 0 || autosensData.absorbing || autosensData.mealCarbs > 0) {
                    autosensData.absorbing = deviation > 0
                    // stop excluding positive deviations as soon as mealCOB=0 if meal has been absorbing for >5h
                    if (autosensData.mealStartCounter > 60 && autosensData.cob < 0.5) {
                        autosensData.absorbing = false
                    }
                    if (!autosensData.absorbing && autosensData.cob < 0.5) {
                        autosensData.mealCarbs = 0.0
                    }
                    // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                    if (autosensData.type != "csf") {
//                                process.stderr.write("(");
                        autosensData.mealStartCounter = 0
                    }
                    autosensData.mealStartCounter++
                    autosensData.type = "csf"
                } else {
                    // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                    val currentBasal = profile.getBasal(bgTime)
                    // always exclude the first 45m after each carb entry
                    //if (iob.iob > currentBasal || uam ) {
                    if (iob.iob > 2 * currentBasal || autosensData.uam || autosensData.mealStartCounter < 9) {
                        autosensData.mealStartCounter++
                        autosensData.uam = deviation > 0
                        autosensData.type = "uam"
                    } else {
                        autosensData.type = "non-meal"
                    }
                }

                // Exclude meal-related deviations (carb absorption) from autosens
                when (autosensData.type) {
                    "non-meal" -> {
                        when {
                            abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL -> {
                                autosensData.pastSensitivity += "="
                                autosensData.validDeviation = true
                            }

                            deviation > 0                                    -> {
                                autosensData.pastSensitivity += "+"
                                autosensData.validDeviation = true
                            }

                            else                                             -> {
                                autosensData.pastSensitivity += "-"
                                autosensData.validDeviation = true
                            }
                        }
                    }

                    "uam"      -> {
                        autosensData.pastSensitivity += "u"
                    }

                    else       -> {
                        autosensData.pastSensitivity += "x"
                    }
                }

                // add an extra negative deviation if a high temp target is running and exercise mode is set
                // TODO AS-FIX
                @Suppress("SimplifyBooleanWithConstants")
                if (false && sp.getBoolean(R.string.key_high_temptarget_raises_sensitivity, SMBDefaults.high_temptarget_raises_sensitivity)) {
                    val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
                    if (tempTarget is ValueWrapper.Existing && tempTarget.value.target() >= 100) {
                        autosensData.extraDeviation.add(-(tempTarget.value.target() - 100) / 20)
                    }
                }

                // add one neutral deviation every 2 hours to help decay over long exclusion periods
                val calendar = GregorianCalendar()
                calendar.timeInMillis = bgTime
                val min = calendar[Calendar.MINUTE]
                val hours = calendar[Calendar.HOUR_OF_DAY]
                if (min in 0..4 && hours % 2 == 0) autosensData.extraDeviation.add(0.0)
                previous = autosensData
                if (bgTime < dateUtil.now()) autosensDataTable.put(bgTime, autosensData)
                aapsLogger.debug(LTag.AUTOSENS, "Running detectSensitivity from: " + dateUtil.dateAndTimeString(oldestTimeWithData) + " to: " + dateUtil.dateAndTimeString(bgTime) + " lastDataTime:" + ads.lastDataTime(dateUtil))
                val sensitivity = activePlugin.activeSensitivity.detectSensitivity(ads, oldestTimeWithData, bgTime)
                aapsLogger.debug(LTag.AUTOSENS, "Sensitivity result: $sensitivity")
                autosensData.autosensResult = sensitivity
                aapsLogger.debug(LTag.AUTOSENS, autosensData.toString())
            }
            iobCobCalculatorPlugin.ads = ads
            Thread {
                SystemClock.sleep(1000)
                rxBus.send(EventAutosensCalculationFinished(cause))
            }.start()
        } finally {
            mWakeLock?.release()
            rxBus.send(EventIobCalculationProgress("", cause))
            aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA thread ended: $from")
            profiler.log(LTag.AUTOSENS, "IobCobOref1Thread", start)
        }
    }
}