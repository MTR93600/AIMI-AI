package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import com.jjoe64.graphview.series.DataPointInterface

interface DataPointWithLabelInterface : DataPointInterface {

    override fun getX(): Double
    override fun getY(): Double
    fun setY(y: Double)

    val label: String?
    val duration: Long
    val shape: PointsWithLabelGraphSeries.Shape?
    val size: Float
    val color: Int
}