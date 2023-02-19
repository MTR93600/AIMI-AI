package info.nightscout.database.transactions

import info.nightscout.database.entities.GlucoseValue
import java.util.TimeZone


/**
 * used by medlink
 */
data class TransactionGlucoseValue(
    val timestamp: Long,
    val value: Double,
    val raw: Double?,
    val noise: Double?,
    val trendArrow: GlucoseValue.TrendArrow,
    val nightscoutId: String? = null,
    val sourceSensor: GlucoseValue.SourceSensor,
    val isValid: Boolean = true,
    val utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    val calibrationFactor: Double? = null,
    val sensorUptime: Int? = null,
    val isig: Double? = null,
    val delta: Double? = null,

)