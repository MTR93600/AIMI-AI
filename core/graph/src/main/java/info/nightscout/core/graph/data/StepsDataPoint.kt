package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Paint
import info.nightscout.database.entities.StepsCount
import info.nightscout.shared.interfaces.ResourceHelper

class StepsDataPoint(
    private val data: StepsCount,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    override fun getX(): Double = (data.timestamp - data.duration).toDouble()
    override fun getY(): Double = data.steps5min.toDouble()
    override fun setY(y: Double) {}

    override val label: String = ""
    override val duration = data.duration
    override val shape = PointsWithLabelGraphSeries.Shape.STEPS
    override val size = 1f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    override fun color(context: Context?): Int = rh.gac(context, info.nightscout.core.ui.R.attr.stepsColor)
}
