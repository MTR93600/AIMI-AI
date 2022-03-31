package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.Round
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

class IsfProfileGraph : GraphView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun show(profile: Profile) {
        removeAllSeries()
        val isfArray: MutableList<DataPoint> = ArrayList()
        var maxIsf = 0.0
        val units = profile.units
        for (hour in 0..23) {
            val isf = Profile.fromMgdlToUnits(profile.getIsfMgdlTimeFromMidnight(hour * 60 * 60), units)
            maxIsf = max(maxIsf, isf)
            isfArray.add(DataPoint(hour.toDouble(), isf))
            isfArray.add(DataPoint((hour + 1).toDouble(), isf))
        }
        val isfDataPoints: Array<DataPoint> = Array(isfArray.size) { i -> isfArray[i] }
        val isfSeries: LineGraphSeries<DataPoint> = LineGraphSeries(isfDataPoints)
        addSeries(isfSeries)
        isfSeries.thickness = 8
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        val maxY = Round.ceilTo(maxIsf * 1.1, 0.5)
        viewport.setMaxY(maxY)
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.labelVerticalWidth = 40
        gridLabelRenderer.verticalLabelsColor = isfSeries.color

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }

    fun show(profile1: Profile, profile2: Profile) {
        removeAllSeries()

        var minIsf = 1000.0
        var maxIsf = 0.0
        val units = profile1.units
        // isf 1
        val isfArray1: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val isf = Profile.fromMgdlToUnits(profile1.getIsfMgdlTimeFromMidnight(hour * 60 * 60), units)
            minIsf = min(minIsf, isf)
            maxIsf = max(maxIsf, isf)
            isfArray1.add(DataPoint(hour.toDouble(), isf))
            isfArray1.add(DataPoint((hour + 1).toDouble(), isf))
        }
        val isfSeries1: LineGraphSeries<DataPoint> = LineGraphSeries(Array(isfArray1.size) { i -> isfArray1[i] })
        addSeries(isfSeries1)
        isfSeries1.thickness = 8
        isfSeries1.isDrawBackground = false

        // isf 2
        val isfArray2: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val isf = Profile.fromMgdlToUnits(profile2.getIsfMgdlTimeFromMidnight(hour * 60 * 60), units)
            minIsf = min(minIsf, isf)
            maxIsf = max(maxIsf, isf)
            isfArray2.add(DataPoint(hour.toDouble(), isf))
            isfArray2.add(DataPoint((hour + 1).toDouble(), isf))
        }
        val isfSeries2: LineGraphSeries<DataPoint> = LineGraphSeries(Array(isfArray2.size) { i -> isfArray2[i] })
        addSeries(isfSeries2)
        isfSeries2.thickness = 8
        isfSeries2.isDrawBackground = false
        isfSeries2.color = context.getColor(R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(Round.floorTo(minIsf / 1.1, 0.5))
        viewport.setMaxY(Round.ceilTo(maxIsf * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }
}