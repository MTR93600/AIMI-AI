package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.RequestDexcomPermissionActivity
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.extensions.fromConstant
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.TrendCalculator
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedLinkPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val medLinkMediator: MedLinkMediator,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(R.drawable.ic_minilink)
        .pluginName(R.string.medlink_app_patched)
        .shortName(R.string.medlink_short)
        .preferencesId(R.xml.pref_bgsourcemedlink)
        .description(R.string.description_source_enlite),
    aapsLogger, rh, injector
), BgSource {

    init {
        if (!config.NSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        (glucoseValue.sourceSensor == GlucoseValue.SourceSensor.MM_ENLITE)
            && sp.getBoolean(R.string.key_medlink_nsupload, false)

    override fun onStart() {
        super.onStart()
        // medLinkMediator.requestPermissionIfNeeded()
    }

    // cannot be inner class because of needed injection
    class MedLinkWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var medLinkPlugin: MedLinkPlugin
        @Inject lateinit var sp: SP
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorker: DataWorker
        @Inject lateinit var xDripBroadcast: XDripBroadcast
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var uel: UserEntryLogger
        @Inject lateinit var trendCalculator: TrendCalculator

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!medLinkPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorker.pickupBundle(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            try {
                val sourceSensor = when (bundle.getString("sensorType") ?: "") {
                    "Enlite" -> GlucoseValue.SourceSensor.MM_ENLITE
                    else     -> GlucoseValue.SourceSensor.UNKNOWN
                }
                val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
                bundle.getBundle("meters")?.let { meters ->
                    for (i in 0 until meters.size()) {
                        meters.getBundle(i.toString())?.let {
                            val timestamp = it.getLong("timestamp")
                            val now = dateUtil.now()
                            val value = it.getInt("meterValue").toDouble()
                            if (timestamp > now - T.months(1).msecs() && timestamp < now) {
                                calibrations.add(
                                    CgmSourceTransaction.Calibration(
                                        timestamp = it.getLong("timestamp"),
                                        value = value,
                                        glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(Profile.unit(value))
                                    )
                                )
                            }
                        }
                    }
                }
                val glucoseValuesBundle = bundle.getBundle("glucoseValues")
                    ?: return Result.failure(workDataOf("Error" to "missing glucoseValues"))
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                for (i in 0 until glucoseValuesBundle.size()) {
                    val glucoseValueBundle = glucoseValuesBundle.getBundle(i.toString())!!
                    val timestamp = glucoseValueBundle.getLong("timestamp")
                    val isig = glucoseValueBundle.getDouble("isig")
                    val deltaSinceLastBg = glucoseValueBundle.getDouble("delta_since_last_bg")
                    val sensorUptime = glucoseValueBundle.getInt("sensor_uptime")
                    val calibrationFactor = glucoseValueBundle.getDouble("calibration_factor")

                    // G5 calibration bug workaround (calibration is sent as glucoseValue too)
                    var valid = true
                    // if (sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE)
                    //     calibrations.forEach { calibration -> if (calibration.timestamp == timestamp) valid = false }
                    if (valid) {
                        val glucoseValue = GlucoseValue(
                            timestamp = timestamp,
                            value = glucoseValueBundle.getDouble("value"),
                            noise = null,
                            raw = null,
                            trendArrow = GlucoseValue.TrendArrow.NONE,
                            sourceSensor = sourceSensor
                        )

                        glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                            timestamp = timestamp,
                            value = glucoseValueBundle.getDouble("value"),
                            noise = null,
                            raw = null,
                            isig = isig,
                            delta = deltaSinceLastBg,
                            sensorUptime = sensorUptime,
                            calibrationFactor = calibrationFactor,
                            trendArrow = trendCalculator.getTrendArrow(glucoseValue),
                            sourceSensor = sourceSensor
                        )
                    }
                }
                val sensorStartTime = if ( bundle.containsKey("sensor_uptime")) {
                   System.currentTimeMillis() - bundle.getLong("sensor_uptime", 0) * 60 * 1000
                } else {
                    null
                }

                repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, sensorStartTime))
                    .doOnError {
                        aapsLogger.error(LTag.DATABASE, "Error while saving values from Dexcom App", it)
                        ret = Result.failure(workDataOf("Error" to it.toString()))
                    }
                    .blockingGet()
                    .also { result ->
                        result.inserted.forEach {
                            xDripBroadcast.send(it)
                            aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        }
                        result.updated.forEach {
                            xDripBroadcast.send(it)
                            aapsLogger.debug(LTag.DATABASE, "Updated bg $it")
                        }
                        result.sensorInsertionsInserted.forEach {
                            uel.log(
                                UserEntry.Action.CAREPORTAL,
                                UserEntry.Sources.Enlite,
                                ValueWithUnit.Timestamp(it.timestamp),
                                ValueWithUnit.TherapyEventType(it.type)
                            )
                            aapsLogger.debug(LTag.DATABASE, "Inserted sensor insertion $it")
                        }
                        result.calibrationsInserted.forEach { calibration ->
                            calibration.glucose?.let { glucoseValue ->
                                uel.log(
                                    UserEntry.Action.CALIBRATION,
                                    UserEntry.Sources.Enlite,
                                    ValueWithUnit.Timestamp(calibration.timestamp),
                                    ValueWithUnit.TherapyEventType(calibration.type),
                                    ValueWithUnit.fromGlucoseUnit(glucoseValue, calibration.glucoseUnit.toString)
                                )
                            }
                            aapsLogger.debug(LTag.DATABASE, "Inserted calibration $calibration")
                        }
                    }
            } catch (e: Exception) {
                aapsLogger.error("Error while processing intent from Dexcom App", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }
            return ret
        }

        fun storeBG(bundle: JSONObject) {
            val glucoseValues = bundle.getJSONArray("glucoseValues")
            dataWorker.enqueue(
                OneTimeWorkRequest.Builder(LocalProfilePlugin.NSProfileWorker::class.java)
                    .setInputData(dataWorker.storeInputData(glucoseValues, null))
                    .build()
            )
            xDripBroadcast.sendSgvs(glucoseValues)
            val calibrations = bundle.getJSONArray("meters")
            dataWorker.enqueue(
                OneTimeWorkRequest.Builder(LocalProfilePlugin.NSProfileWorker::class.java)
                    .setInputData(dataWorker.storeInputData(calibrations, null))
                    .build()
            )
            doWork()

        }
    }

    companion object {

        private val PACKAGE_NAMES = arrayOf(
            "com.dexcom.cgm.region1.mgdl", "com.dexcom.cgm.region1.mmol",
            "com.dexcom.cgm.region2.mgdl", "com.dexcom.cgm.region2.mmol",
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol", "com.dexcom.g6"
        )
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }

    class MedLinkMediator @Inject constructor(val context: Context) {

        fun requestPermissionIfNeeded() {
            if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(context, RequestDexcomPermissionActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        fun findDexcomPackageName(): String? {
            val packageManager = context.packageManager
            for (packageInfo in packageManager.getInstalledPackages(0)) {
                if (PACKAGE_NAMES.contains(packageInfo.packageName)) return packageInfo.packageName
            }
            return null
        }
    }

    // override fun handleNewData(intent: Intent) {
    //     if (!isEnabled(PluginType.BGSOURCE)) return
    //     try {
    //         val sensorType = intent.getStringExtra("sensorType") ?: ""
    //         val glucoseValues = intent.getBundleExtra("glucoseValues")
    //         val isigValues = intent.getBundleExtra("isigValues")
    //         for (i in 0 until glucoseValues.size()) {
    //             glucoseValues.getBundle(i.toString())?.let { glucoseValue ->
    //                 val bgReading = BgReading()
    //                 bgReading.value = glucoseValue.getDouble("value")
    //                 bgReading.direction = glucoseValue.getString("direction")
    //                 bgReading.date = glucoseValue.getLong("date")
    //                 bgReading.raw = 0.0
    //                 val dbHelper = MainApp.getDbHelper()
    //                 // aapsLogger.info(LTag.DATABASE, "bgneedupdate? "+dbHelper.thisBGNeedUpdate(bgReading))
    //                 if (dbHelper.thisBGNeedUpdate(bgReading)) {
    //                     if (dbHelper.createIfNotExists(bgReading, "MedLink$sensorType")) {
    //                         if (sp.getBoolean(R.string.key_medlink_nsupload, false) &&
    //                             (isigValues == null || isigValues.size() == 0)) {
    //                             nsUpload.uploadBg(bgReading, "AndroidAPS-MedLink$sensorType")
    //                         }
    //                         if (sp.getBoolean(R.string.key_medlink_xdripupload, false)) {
    //                             nsUpload.sendToXdrip(bgReading)
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //         if (isigValues != null) {
    //             for (i in 0 until isigValues.size()) {
    //                 isigValues.getBundle(i.toString())?.let { isigValue ->
    //                     val dbHelper = MainApp.getDbHelper()
    //
    //                     val bgReading = BgReading()
    //                     bgReading.value = isigValue.getDouble("value")
    //                     bgReading.direction = isigValue.getString("direction")
    //                     bgReading.date = isigValue.getLong("date")
    //                     bgReading.raw = 0.0
    //
    //                     val sensReading = SensorDataReading()
    //                     sensReading.bgValue = isigValue.getDouble("value")
    //                     sensReading.direction = isigValue.getString("direction")
    //                     sensReading.date = isigValue.getLong("date")
    //                     sensReading.isig = isigValue.getDouble("isig")
    //                     sensReading.deltaSinceLastBG = isigValue.getDouble("delta")
    //                     sensReading.sensorUptime = dbHelper.sensorAge
    //                     sensReading.bgReading = bgReading
    //                     if(isigValue.getDouble("calibrationFactor") == 0.0){
    //                         sensReading.calibrationFactor = dbHelper.getCalibrationFactor(sensReading.date)?.calibrationFactor ?: 0.0
    //                     }else {
    //                         sensReading.calibrationFactor = isigValue.getDouble("calibrationFactor")
    //                     }
    //                     // aapsLogger.info(LTag.DATABASE, "bgneedupdate? "+dbHelper.thisBGNeedUpdate(bgReading))
    //                     if (dbHelper.thisSensNeedUpdate(sensReading)) {
    //                         if (dbHelper.createIfNotExists(sensReading, "MedLink$sensorType")) {
    //                             if (sp.getBoolean(R.string.key_medlink_nsupload, false)) {
    //                                 //
    //                                 nsUpload.uploadEnliteData(sensReading, "AndroidAPS-MedLink$sensorType")
    //                             }
    //                             if (sp.getBoolean(R.string.key_medlink_xdripupload, false)) {
    //                                 nsUpload.sendToXdrip(sensReading.bgReading)
    //                             }
    //                         }
    //                     }
    //                     val calibrationFactor = CalibrationFactorReading()
    //                     calibrationFactor.date = isigValue.getLong("date")
    //                     calibrationFactor.calibrationFactor = isigValue.getDouble("calibrationFactor")
    //                     dbHelper.createIfNotExists(calibrationFactor, "MedLink$sensorType")
    //                 }
    //             }
    //         }
    //         val meters = intent.getBundleExtra("meters")
    //         for (i in 0 until meters.size()) {
    //             val meter = meters.getBundle(i.toString())
    //             meter?.let {
    //                 val timestamp = it.getLong("timestamp")
    //                 aapsLogger.info(LTag.DATABASE, "meters timestamp $timestamp")
    //                 val now = DateUtil.now()
    //                 if (timestamp > now - T.months(1).msecs() && timestamp < now)
    //                     if (MainApp.getDbHelper().getCareportalEventFromTimestamp(timestamp) == null) {
    //                         val jsonObject = JSONObject()
    //                         jsonObject.put("enteredBy", "AndroidAPS-MedLink$sensorType")
    //                         jsonObject.put("created_at", DateUtil.toISOString(timestamp))
    //                         jsonObject.put("eventType", CareportalEvent.BGCHECK)
    //                         jsonObject.put("glucoseType", "Finger")
    //                         jsonObject.put("glucose", meter.getDouble("meterValue"))
    //                         jsonObject.put("units", Constants.MGDL)
    //                         jsonObject.put("mills", timestamp)
    //
    //                         val careportalEvent = CareportalEvent(injector)
    //                         careportalEvent.date = timestamp
    //                         careportalEvent.source = Source.USER
    //                         careportalEvent.eventType = CareportalEvent.BGCHECK
    //                         careportalEvent.json = jsonObject.toString()
    //                         MainApp.getDbHelper().createOrUpdate(careportalEvent)
    //                         nsUpload.uploadCareportalEntryToNS(jsonObject)
    //                     }
    //             }
    //         }
    //
    //         if (sp.getBoolean(R.string.key_medlink_lognssensorchange, false) && intent.hasExtra("sensorInsertionTime")) {
    //             intent.extras?.let {
    //                 val sensorInsertionTime = it.getLong("sensorInsertionTime") * 1000
    //                 val now = DateUtil.now()
    //                 if (sensorInsertionTime > now - T.months(1).msecs() && sensorInsertionTime < now)
    //                     if (MainApp.getDbHelper().getCareportalEventFromTimestamp(sensorInsertionTime) == null) {
    //                         val jsonObject = JSONObject()
    //                         jsonObject.put("enteredBy", "AndroidAPS-MedLink$sensorType")
    //                         jsonObject.put("created_at", DateUtil.toISOString(sensorInsertionTime))
    //                         jsonObject.put("eventType", CareportalEvent.SENSORCHANGE)
    //                         val careportalEvent = CareportalEvent(injector)
    //                         careportalEvent.date = sensorInsertionTime
    //                         careportalEvent.source = Source.USER
    //                         careportalEvent.eventType = CareportalEvent.SENSORCHANGE
    //                         careportalEvent.json = jsonObject.toString()
    //                         MainApp.getDbHelper().createOrUpdate(careportalEvent)
    //                         nsUpload.uploadCareportalEntryToNS(jsonObject)
    //                     }
    //             }
    //         }
    //     } catch (e: Exception) {
    //         aapsLogger.error("Error while processing intent from MedLink App", e)
    //     }
    // }

    // override fun syncBgWithTempId(
    //     bgValues: List<BgSync.BgHistory.BgValue>,
    //     calibrations: List<BgSync.BgHistory.Calibration>
    // ): Boolean {
    //
    //     // if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
    //     val glucoseValue = bgValues.map { bgValue ->
    //         CgmSourceTransaction.TransactionGlucoseValue(
    //             timestamp = bgValue.timestamp,
    //             raw = bgValue.raw,
    //             noise = bgValue.noise,
    //             value = bgValue.value,
    //             trendArrow = GlucoseValue.TrendArrow.NONE,
    //             sourceSensor = bgValue.sourceSensor.toDbType(),
    //             isig = bgValue.isig
    //         )
    //     }
    //     val calibrations = calibrations.map{
    //             calibration ->
    //         CgmSourceTransaction.Calibration(timestamp = calibration.timestamp,
    //                                          value = calibration.value, glucoseUnit = calibration.glucoseUnit.toDbType() )
    //     }
    //
    // }
    //
    // override fun addBgTempId(
    //     timestamp: Long,
    //     raw: Double?,
    //     value: Double,
    //     noise: Double?,
    //     arrow: BgSync.BgArrow,
    //     sourceSensor: BgSync.SourceSensor,
    //     isig: Double?,
    //     calibrationFactor: Double?,
    //     sensorUptime: Int?
    // ): Boolean {
    //     val glucoseValue = listOf(
    //         GlucoseValue(timestamp = timestamp,
    //                      raw = raw,
    //                      noise = noise,
    //                      value = value,
    //                      trendArrow = GlucoseValue.TrendArrow.NONE,
    //                      sourceSensor = sourceSensor.toDbType(),
    //                      isig = isig).toj
    //         // CgmSourceTransaction.TransactionGlucoseValue(
    //         //     timestamp = timestamp,
    //         //     raw = raw,
    //         //     noise = noise,
    //         //     value = value,
    //         //     trendArrow = GlucoseValue.TrendArrow.NONE,
    //         //     sourceSensor = sourceSensor.toDbType(),
    //         //     isig = isig
    //         // )
    //     )
    //     val calibrations = listOf<CgmSourceTransaction.Calibration>()
    //
    //     repository.runTransactionForResult(CgmSourceTransaction(glucoseValue, calibrations = calibrations, sensorInsertionTime = null))
    //         .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bg", it) }
    //         .blockingGet()
    //         .also { result ->
    //             result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted Bg $it") }
    //             return result.inserted.size > 0
    //         }
    // }
}
