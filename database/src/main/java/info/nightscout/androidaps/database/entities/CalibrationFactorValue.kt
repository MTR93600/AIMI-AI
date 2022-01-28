package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_CALIBRATION_FACTOR
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(
    tableName = TABLE_CALIBRATION_FACTOR,
    foreignKeys = [ForeignKey(
        entity = CalibrationFactorValue::class,
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
data class CalibrationFactorValue(
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
    var calibrationFactor: Double?
    ) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: CalibrationFactorValue): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            calibrationFactor == other.calibrationFactor

    fun onlyNsIdAdded(previous: CalibrationFactorValue): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    fun isRecordDeleted(other: CalibrationFactorValue): Boolean =
        isValid && !other.isValid


}
