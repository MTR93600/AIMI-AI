package info.nightscout.androidaps.data

import info.nightscout.androidaps.database.entities.GlucoseValue
import kotlin.concurrent.fixedRateTimer

class EnliteInMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0, var interpolated: Boolean = false,
                                             var lastTimestamp: Long = 0L, var lastValue: Double = 0.0,
var calibronFactor: Double = 0.0, var isig: Double = 0.0,
                                             var sensorUptime: Int = 0,
                                             var deltaSinceLastBG: Double = 0.0,
                                             var sourceSensor: GlucoseValue.SourceSensor,var trendArrow: GlucoseValue.TrendArrow
) {

    fun isUnknown(): Boolean {
        return sourceSensor === GlucoseValue.SourceSensor.UNKNOWN
    }

    fun glucoseValue(): GlucoseValue {
        return GlucoseValue(value = value, timestamp = timestamp, trendArrow = trendArrow, sourceSensor = sourceSensor, noise = null, raw = value)
    }

    constructor(gv: GlucoseValue, lastGv: GlucoseValue) : this(gv.timestamp, gv.value, false, lastGv.timestamp, lastGv.value,
    sourceSensor = gv.sourceSensor,
    trendArrow = gv.trendArrow)
    // var generated : value doesn't correspond to real value with timestamp close to real BG
}