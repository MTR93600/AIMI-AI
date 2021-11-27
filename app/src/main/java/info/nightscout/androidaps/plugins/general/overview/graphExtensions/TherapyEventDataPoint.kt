package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.graphics.Color
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class TherapyEventDataPoint @Inject constructor(
    val data: TherapyEvent,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val translator: Translator
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()

    override fun getY(): Double {
        val units = profileFunction.getUnits()
        if (data.type == TherapyEvent.Type.NS_MBG) return Profile.fromMgdlToUnits(data.glucose!!, units)
        if (data.glucose != null && data.glucose != 0.0) {
            var mmol = 0.0
            var mgdl = 0.0
            if (data.glucoseUnit == TherapyEvent.GlucoseUnit.MGDL) {
                mgdl = data.glucose!!
                mmol = data.glucose!! * Constants.MGDL_TO_MMOLL
            }
            if (data.glucoseUnit == TherapyEvent.GlucoseUnit.MMOL) {
                mmol = data.glucose!!
                mgdl = data.glucose!! * Constants.MMOLL_TO_MGDL
            }
            return Profile.toUnits(mgdl, mmol, units)
        }
        return yValue
    }

    override fun setY(y: Double) {
        yValue = y
    }

    override val label get() = if (data.note.isNullOrBlank().not()) data.note else translator.translate(data.type)
    override val duration get() = data.duration
    override val shape
        get() =
            when {
                data.type == TherapyEvent.Type.NS_MBG                -> PointsWithLabelGraphSeries.Shape.MBG
                data.type == TherapyEvent.Type.FINGER_STICK_BG_VALUE -> PointsWithLabelGraphSeries.Shape.BGCHECK
                data.type == TherapyEvent.Type.ANNOUNCEMENT          -> PointsWithLabelGraphSeries.Shape.ANNOUNCEMENT
                data.type == TherapyEvent.Type.APS_OFFLINE           -> PointsWithLabelGraphSeries.Shape.OPENAPSOFFLINE
                data.type == TherapyEvent.Type.EXERCISE              -> PointsWithLabelGraphSeries.Shape.EXERCISE
                duration > 0                                         -> PointsWithLabelGraphSeries.Shape.GENERALWITHDURATION
                else                                                 -> PointsWithLabelGraphSeries.Shape.GENERAL
            }

    override val size get() = if (rh.gb(R.bool.isTablet)) 12.0f else 10.0f
    override val color
        get() =
            when (data.type) {
                TherapyEvent.Type.ANNOUNCEMENT          -> rh.gc(R.color.notificationAnnouncement)
                TherapyEvent.Type.NS_MBG                -> Color.RED
                TherapyEvent.Type.FINGER_STICK_BG_VALUE -> Color.RED
                TherapyEvent.Type.EXERCISE              -> Color.BLUE
                TherapyEvent.Type.APS_OFFLINE           -> Color.GRAY and -0x7f000001
                else                                    -> Color.GRAY
            }
}