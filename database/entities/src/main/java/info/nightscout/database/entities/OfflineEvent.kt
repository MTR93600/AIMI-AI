package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_OFFLINE_EVENTS,
    foreignKeys = [ForeignKey(
        entity = OfflineEvent::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("isValid"),
        Index("nightscoutId"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class OfflineEvent(
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
    var reason: Reason,
    override var duration: Long // in millis
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    fun contentEqualsTo(other: OfflineEvent): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            reason == other.reason &&
            duration == other.duration &&
            isValid == other.isValid

    fun onlyNsIdAdded(previous: OfflineEvent): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    fun isRecordDeleted(other: OfflineEvent): Boolean =
        isValid && !other.isValid

    enum class Reason {
        DISCONNECT_PUMP,
        SUSPEND,
        DISABLE_LOOP,
        SUPER_BOLUS,
        OTHER
        ;

        companion object {

            fun fromString(reason: String?) = values().firstOrNull { it.name == reason } ?: OTHER
        }
    }
}