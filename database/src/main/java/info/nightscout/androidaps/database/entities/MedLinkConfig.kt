package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_MEDLINK_CONFIG
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry

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
    override var interfaceIDs_backing: info.nightscout.androidaps.database.embedments.InterfaceIDs? = null,
    var currentFrequency: kotlin.Int
) : TraceableDBEntry {
    fun contentEqualsTo(other: MedLinkConfig): Boolean {
        if (isValid != other.isValid) return false
        if (currentFrequency != other.currentFrequency) return false
        return true
    }
}