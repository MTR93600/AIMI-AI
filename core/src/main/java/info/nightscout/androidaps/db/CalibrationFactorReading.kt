package info.nightscout.androidaps.db

import androidx.annotation.NonNull
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

/**
 * Created by Dirceu on 16/04/21.
 */
@DatabaseTable(tableName = "CalibrationFactor")
class CalibrationFactorReading : DataPointWithLabelInterface {

    @Inject
    lateinit var aapsLogger: AAPSLogger

    @Inject
    lateinit var defaultValueHelper: DefaultValueHelper

    @Inject
    lateinit var profileFunction: ProfileFunction

    @Inject
    lateinit var resourceHelper: ResourceHelper

    @Inject
    lateinit var dateUtil: DateUtil

    @DatabaseField(id = true)
    var date: Long = 0

    @DatabaseField
    var calibrationFactor = 0.0


    @NonNull
    override fun toString(): String {
        return "CalibrationFactorReading{" +
            "date=" + date +
            ", date=" + dateUtil!!.dateAndTimeString(date) +
            ", calibrationFactor=" + calibrationFactor +
            '}'
    }

    override fun getX(): Double {
        return date.toDouble()
    }

    override fun getY(): Double {
        return calibrationFactor
    }

    override fun setY(y: Double) {
        TODO("Not yet implemented")
    }

    override fun getLabel(): String? {
        return null
    }

    override fun getDuration(): Long {
        return 0
    }

    override fun getShape(): PointsWithLabelGraphSeries.Shape {
        // if (isPrediction()) return PointsWithLabelGraphSeries.Shape.PREDICTION else
            return PointsWithLabelGraphSeries.Shape.BG
    }
    override fun getSize(): Float {
        return 1F
    }

    override fun getColor(): Int {
        return resourceHelper?.gc(R.color.inrange) ?: -1
    }

    fun isEqual(other: CalibrationFactorReading?): Boolean {
        if (date != other?.date) {
            aapsLogger!!.debug(LTag.GLUCOSE, "Comparing different")
            return false
        }
        return calibrationFactor == other.calibrationFactor
    }

    fun copyFrom(other: CalibrationFactorReading) {
        if (date != other.date) {
            aapsLogger!!.error(LTag.GLUCOSE, "Copying different")
            return
        }
        calibrationFactor = other.calibrationFactor
    }
}
