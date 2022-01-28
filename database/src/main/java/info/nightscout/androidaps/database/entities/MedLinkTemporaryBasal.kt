package info.nightscout.androidaps.database.entities

import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import java.util.*

/**
 * Created by Dirceu on 26/05/21.
 */
// data class MedLinkTemporaryBasal(
//     @PrimaryKey(autoGenerate = true)
//     var id: Long = 0,
//     var version: Int = 0,
//     var dateCreated: Long = -1,
//     var isValid: Boolean = true,
//     var referenceId: Long? = null,
//     var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
//     var timestamp: Long,
//     var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
//     var type: TemporaryBasal.Type,
//     var isAbsolute: Boolean,
//     var rate: Double,
//     var duration: Long
// )  {
//
//     var percentRate = 0
//     var desiredRate = 0.0
//     var desiredPct = 0
//     fun MedLinkTemporaryBasal.toString(): String {
//         return "MedLinkTemporaryBasal{" +
//             "desiredRate=" + desiredRate +
//             ", desiredPct=" + desiredPct +
//             '}' + super.toString()
//     }
//
// }