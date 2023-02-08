package info.nightscout.interfaces.pump

/**
 * Medlink glucosevalue
 */
class EnliteInMemoryGlucoseValue constructor(var timestamp: Long = 0L,
                                             var value: Double = 0.0,
                                             var interpolated: Boolean = false,
                                             var lastTimestamp: Long = 0L,
                                             var lastValue: Double = 0.0,
                                             var calibronFactor: Double = 0.0,
                                             var isig: Double = 0.0,
                                             var sensorUptime: Int = 0,
                                             var deltaSinceLastBG: Double = 0.0,
                                             var sourceSensor: SourceSensor = SourceSensor.MM_ENLITE,
                                             // var trendArrow: GlucoseValue.TrendArrow = GlucoseValue.TrendArrow.NONE
) {

    fun isCalibration(): Boolean {
        return sourceSensor == SourceSensor.CALIBRATION
    }
    //
    // fun glucoseValue(): GlucoseValue {
    //     return GlucoseValue(value = value, timestamp = timestamp, trendArrow = trendArrow, sourceSensor = sourceSensor, noise = null, raw = value)
    // }
}
enum class SourceSensor {
    MM_ENLITE,
    CALIBRATION

}
