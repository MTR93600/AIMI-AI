package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

/**
 * Created by Dirceu on 10/04/21.
 */
@Entity(
    tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("sourceSensor"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class SensorDataReading(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var raw: Double?,
    var value: Double,
    var trendArrow: GlucoseValue.TrendArrow,
    var noise: Double?,
    var sourceSensor: GlucoseValue.SourceSensor,
    var calibrationFactor: Double?,
    var isig: Int?,
    var sensorUptime: Int?,
    var deltaSinceLastBG: Double?

) : TraceableDBEntry, DBEntryWithTime {

    @JvmField var bgReading: GlucoseValue? = null

    fun contentEqualsTo(other: GlucoseValue): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            raw == other.raw &&
            value == other.value &&
            trendArrow == other.trendArrow &&
            noise == other.noise &&
            sourceSensor == other.sourceSensor

    fun onlyNsIdAdded(previous: GlucoseValue): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    fun isRecordDeleted(other: GlucoseValue): Boolean =
        isValid && !other.isValid

}
