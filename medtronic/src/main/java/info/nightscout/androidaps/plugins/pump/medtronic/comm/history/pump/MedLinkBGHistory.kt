package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.SensorDataReading
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by Dirceu on 15/04/21.
 */
class MedLinkBGHistory(var readingMemAddress: Integer,
                       var readings: List<BgReading>,
                       var injector: HasAndroidInjector) {

    var isigHistory: List<Double> = ArrayList();
    lateinit var isigMemAddress: Integer

    fun validateHistoryEntries(): Boolean {
        return isigHistory.size == readings.size && isigMemAddress == readingMemAddress
    }

    fun buildSensReadings(): List<SensorDataReading> {
        var result = listOf<SensorDataReading>()
        if (validateHistoryEntries()) {

            result = readings.zip(isigHistory) { reading, isig ->
                SensorDataReading(injector, reading, isig,
                    CalibrationFactor.getCalibrationFactor(reading.date))

            }
        }
        return result;
    }
}