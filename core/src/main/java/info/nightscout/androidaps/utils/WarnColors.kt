package info.nightscout.androidaps.utils

import android.graphics.Color
import android.widget.TextView
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.isOlderThan
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarnColors @Inject constructor(val rh: ResourceHelper) {

    private val normalColor = Color.WHITE
    private val warnColor = Color.YELLOW
    private val urgentColor = Color.RED

    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) =
        view?.setTextColor(when {
            value >= urgentLevel -> urgentColor
            value >= warnLevel   -> warnColor
            else                 -> normalColor
        })

    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) =
        view?.setTextColor(when {
            value <= urgentLevel -> urgentColor
            value <= warnLevel   -> warnColor
            else                 -> normalColor
        })

    fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double) =
        view?.setTextColor(when {
            therapyEvent.isOlderThan(urgentThreshold) -> rh.gc(R.color.low)
            therapyEvent.isOlderThan(warnThreshold)   -> rh.gc(R.color.high)
            else                                      -> Color.WHITE
        })
}