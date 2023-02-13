// package info.nightscout.androidaps.db
//
// import androidx.annotation.NonNull
// import com.j256.ormlite.field.DatabaseField
// import com.j256.ormlite.table.DatabaseTable
// import info.nightscout.androidaps.database.entities.CalibrationFactorValue
// import info.nightscout.androidaps.interfaces.ProfileFunction
//
// import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
// import info.nightscout.androidaps.utils.DateUtil
// import info.nightscout.androidaps.utils.DefaultValueHelper
// import info.nightscout.androidaps.utils.resources.ResourceHelper
// import info.nightscout.shared.logging.AAPSLogger
// import info.nightscout.shared.logging.LTag
// import javax.inject.Inject
//
// /**
//  * Created by Dirceu on 16/04/21.
//  */
// @DatabaseTable(tableName = "CalibrationFactor")
// class CalibrationFactorDataPoint  @Inject constructor(
//     val data: CalibrationFactorValue,
//     private val defaultValueHelper: DefaultValueHelper,
//     private val profileFunction: ProfileFunction,
//     private val rh: ResourceHelper ): DataPointWithLabelInterface {
//
//     @Inject
//     lateinit var aapsLogger: AAPSLogger
//
//     @Inject
//     lateinit var dateUtil: DateUtil
//
//     @DatabaseField(id = true)
//     var date: Long = 0
//
//     @DatabaseField
//     var calibrationFactor = 0.0
//
//
//     @NonNull
//     override fun toString(): String {
//         return "CalibrationFactorReading{" +
//             "date=" + date +
//             ", date=" + dateUtil!!.dateAndTimeString(date) +
//             ", calibrationFactor=" + calibrationFactor +
//             '}'
//     }
//
//     override fun getX(): Double {
//         return date.toDouble()
//     }
//
//     override fun getY(): Double {
//         return calibrationFactor
//     }
//
//     override fun setY(y: Double) {
//         TODO("Not yet implemented")
//     }
//
//
//
//
//
//
//     fun isEqual(other: CalibrationFactorDataPoint?): Boolean {
//         if (date != other?.date) {
//             aapsLogger!!.debug(LTag.GLUCOSE, "Comparing different")
//             return false
//         }
//         return calibrationFactor == other.calibrationFactor
//     }
//
//     fun copyFrom(other: CalibrationFactorDataPoint) {
//         if (date != other.date) {
//             aapsLogger!!.error(LTag.GLUCOSE, "Copying different")
//             return
//         }
//         calibrationFactor = other.calibrationFactor
//     }
// }
