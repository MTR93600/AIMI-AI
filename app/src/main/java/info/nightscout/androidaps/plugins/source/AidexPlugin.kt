package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.receivers.Intents
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AidexPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val buildHelper: BuildHelper,
    private val config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon((R.drawable.ic_blooddrop_48))
        .pluginName(R.string.aidex)
        .shortName(R.string.aidex_short)
        .description(R.string.description_source_aidex),
    aapsLogger, rh, injector
), BgSource {

    private var advancedFiltering = false

    /**
     * Aidex App doesn't have upload to NS
     */
    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean = true

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    // Allow only for pumpcontrol or dev & engineering_mode
    override fun specialEnableCondition(): Boolean {
        return config.APS.not() || buildHelper.isDev() && buildHelper.isEngineeringMode()
    }

    // cannot be inner class because of needed injection
    class AidexWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var aidexPlugin: AidexPlugin
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var dataWorker: DataWorker

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!aidexPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorker.pickupBundle(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex data: $bundle")

            if (bundle.containsKey(Intents.AIDEX_TRANSMITTER_SN)) aapsLogger.debug(LTag.BGSOURCE, "transmitterSerialNumber: " + bundle.getString(Intents.AIDEX_TRANSMITTER_SN))
            if (bundle.containsKey(Intents.AIDEX_SENSOR_ID)) aapsLogger.debug(LTag.BGSOURCE, "sensorId: " + bundle.getString(Intents.AIDEX_SENSOR_ID))

            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()

            val timestamp = bundle.getLong(Intents.AIDEX_TIMESTAMP, 0)
            val bgType = bundle.getString(Intents.AIDEX_BG_TYPE, "mg/dl")
            val bgValue = bundle.getDouble(Intents.AIDEX_BG_VALUE, 0.0)

            val bgValueTarget = if (bgType.equals("mg/dl")) bgValue else bgValue * Constants.MMOLL_TO_MGDL

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex broadcast [time=$timestamp, bgType=$bgType, value=$bgValue, targetValue=$bgValueTarget")

            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = timestamp,
                value = bgValueTarget,
                raw = null,
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.fromString(bundle.getString(Intents.AIDEX_BG_SLOPE_NAME)),
                sourceSensor = GlucoseValue.SourceSensor.AIDEX
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Aidex", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.all().forEach {
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            return ret
        }
    }
}
