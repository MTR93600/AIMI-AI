package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_DEVICE_STATUS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import java.util.*

@Entity(tableName = TABLE_DEVICE_STATUS,
    foreignKeys = [],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("timestamp")
    ])
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    @Embedded
    var interfaceIDs_backing: InterfaceIDs? = null,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var device: String? = null,
    var pump: String? = null,
    var enacted: String? = null,
    var suggested: String? = null,
    var iob: String? = null,
    var uploaderBattery: Int = 0,
    var configuration: String? = null

) : DBEntryWithTime {

    var interfaceIDs: InterfaceIDs
        get() {
            var value = this.interfaceIDs_backing
            if (value == null) {
                value = InterfaceIDs()
                interfaceIDs_backing = value
            }
            return value
        }
        set(value) {
            interfaceIDs_backing = value
        }
}