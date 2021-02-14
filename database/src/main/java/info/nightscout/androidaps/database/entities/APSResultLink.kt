package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.TABLE_APS_RESULT_LINKS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry

@Entity(tableName = TABLE_APS_RESULT_LINKS,
        foreignKeys = [ForeignKey(
                entity = APSResult::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("apsResultId")), ForeignKey(

                entity = Bolus::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("smbId")), ForeignKey(

                entity = TemporaryBasal::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("tbrId")), ForeignKey(

                entity = APSResultLink::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("referenceId"))],
        indices = [Index("referenceId"), Index("apsResultId"),
            Index("smbId"), Index("tbrId")])
data class APSResultLink(
        @PrimaryKey(autoGenerate = true)
        override var id: Long = 0,
        override var version: Int = 0,
        override var dateCreated: Long = -1,
        override var isValid: Boolean = true,
        override var referenceId: Long? = null,
        @Embedded
        override var interfaceIDs_backing: InterfaceIDs? = null,
        var apsResultId: Long,
        var smbId: Long? = null,
        var tbrId: Long? = null
) : TraceableDBEntry {
    override val foreignKeysValid: Boolean
        get() = super.foreignKeysValid && apsResultId != 0L && smbId != 0L && tbrId != 0L
}