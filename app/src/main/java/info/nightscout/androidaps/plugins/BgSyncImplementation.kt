package info.nightscout.androidaps.plugins

import android.os.Bundle
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.source.MedLinkPlugin
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class BgSyncImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository
) : BgSync {

    @Inject lateinit var medlinkWorker: MedLinkPlugin.MedLinkWorker

    override fun syncBgWithTempId(
        bundle: Bundle
    ) {
        val jsonObject = JSONObject()
        val keys = bundle.keySet()
        for (key in keys) {
            try {
                jsonObject.put(key, bundle[key])
            } catch (e: JSONException) {
                //Handle exception here
            }
        }
        medlinkWorker.storeBG(jsonObject)
    }

        override fun syncBgWithTempId(
        bgValues: List<BgSync.BgHistory.BgValue>,
        calibrations: List<BgSync.BgHistory.Calibration>
    ): Boolean {

        // if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val glucoseValue = bgValues.map { bgValue ->
            CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = bgValue.timestamp,
                raw = bgValue.raw,
                noise = bgValue.noise,
                value = bgValue.value,
                trendArrow = GlucoseValue.TrendArrow.NONE,
                sourceSensor = bgValue.sourceSensor.toDbType(),
                isig = bgValue.isig
            )
        }
        val calibrations = calibrations.map{
            calibration ->
            CgmSourceTransaction.Calibration(timestamp = calibration.timestamp,
            value = calibration.value, glucoseUnit = calibration.glucoseUnit.toDbType() )
        }
        repository.runTransactionForResult(CgmSourceTransaction(glucoseValue, calibrations = calibrations, sensorInsertionTime = null))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving BG", it) }
            .blockingGet()
            .also { result ->
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated BG $it") }
                return result.updated.size > 0
            }

    }

    override fun addBgTempId(
        timestamp: Long,
        raw: Double?,
        value: Double,
        noise: Double?,
        arrow: BgSync.BgArrow,
        sourceSensor: BgSync.SourceSensor,
        isig: Double?,
        calibrationFactor: Double?,
        sensorUptime: Int?
    ): Boolean {
        val glucoseValue = listOf(
            CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = timestamp,
                raw = raw,
                noise = noise,
                value = value,
                trendArrow = GlucoseValue.TrendArrow.NONE,
                sourceSensor = sourceSensor.toDbType(),
                isig = isig
            )
        )
        val calibrations = listOf<CgmSourceTransaction.Calibration>()
        repository.runTransactionForResult(CgmSourceTransaction(glucoseValue, calibrations = calibrations, sensorInsertionTime = null))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bg", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted Bg $it") }
                return result.inserted.size > 0
            }
    }


}