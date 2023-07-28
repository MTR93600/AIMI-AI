package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.*

/** Steps count values measured by a user smart watch or the like. */
@Entity(
    tableName = TABLE_STEPS_COUNT,
    indices = [Index("id"), Index("timestamp")]
)
data class StepsCount(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    /** Duration milliseconds */
    override var duration: Long,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    override var timestamp: Long,
    var steps5min: Int,
    var steps10min: Int,
    var steps15min: Int,
    var steps30min: Int,
    var steps60min: Int,
    /** Source device that measured the steps count. */
    var device: String,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    fun contentEqualsTo(other: StepsCount): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                steps5min == other.steps5min &&
                steps10min == other.steps10min &&
                steps15min == other.steps15min &&
                steps30min == other.steps30min &&
                steps60min == other.steps60min &&
                isValid == other.isValid)
    }
}
