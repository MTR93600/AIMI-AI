package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTime
import info.nightscout.database.entities.interfaces.TraceableDBEntry

@Entity(
    tableName = TABLE_MEDLINK_CONFIG,
    foreignKeys = [ForeignKey(
        entity = MedLinkConfig::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id")
    ]
)
data class MedLinkConfig(
    @PrimaryKey(autoGenerate = true)
    override var id: kotlin.Long = 0,
    override var version: kotlin.Int = 0,
    override var dateCreated: kotlin.Long = -1,
    override var isValid: kotlin.Boolean = true,
    override var referenceId: kotlin.Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null,
    var currentFrequency: kotlin.Int,
    override var timestamp: Long,
    override var utcOffset: Long
) : TraceableDBEntry, DBEntryWithTime {
    fun contentEqualsTo(other: MedLinkConfig): Boolean {
        if (isValid != other.isValid) return false
        if (currentFrequency != other.currentFrequency) return false
        return true
    }
}