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

class BasalProfileGraph : GraphView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun show(profile: Profile) {
        removeAllSeries()
        val basalArray: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val basal = profile.getBasalTimeFromMidnight(hour * 60 * 60)
            basalArray.add(DataPoint(hour.toDouble(), basal))
            basalArray.add(DataPoint((hour + 1).toDouble(), basal))
        }
        val basalDataPoints: Array<DataPoint> = Array(basalArray.size) { i -> basalArray[i] }
        val basalSeries: LineGraphSeries<DataPoint> = LineGraphSeries(basalDataPoints)
        addSeries(basalSeries)
        basalSeries.thickness = 8
        basalSeries.isDrawBackground = true
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(profile.getMaxDailyBasal() * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.verticalLabelsColor = basalSeries.color

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }

    fun show(profile1: Profile, profile2: Profile) {
        removeAllSeries()

        // profile 1
        val basalArray1: MutableList<DataPoint> = ArrayList()
        var minBasal = 1000.0
        var maxBasal = 0.0
        for (hour in 0..23) {
            val basal = profile1.getBasalTimeFromMidnight(hour * 60 * 60)
            minBasal = min(minBasal, basal)
            maxBasal = max(maxBasal, basal)
            basalArray1.add(DataPoint(hour.toDouble(), basal))
            basalArray1.add(DataPoint((hour + 1).toDouble(), basal))
        }
        val basalSeries1: LineGraphSeries<DataPoint> = LineGraphSeries(Array(basalArray1.size) { i -> basalArray1[i] })
        addSeries(basalSeries1)
        basalSeries1.thickness = 8
        basalSeries1.isDrawBackground = true

        // profile 2
        val basalArray2: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val basal = profile2.getBasalTimeFromMidnight(hour * 60 * 60)
            minBasal = min(minBasal, basal)
            maxBasal = max(maxBasal, basal)
            basalArray2.add(DataPoint(hour.toDouble(), basal))
            basalArray2.add(DataPoint((hour + 1).toDouble(), basal))
        }
        val basalSeries2: LineGraphSeries<DataPoint> = LineGraphSeries(Array(basalArray2.size) { i -> basalArray2[i] })
        addSeries(basalSeries2)
        basalSeries2.thickness = 8
        basalSeries2.isDrawBackground = false
        basalSeries2.color = context.getColor(R.color.examinedProfile)
        basalSeries2.backgroundColor = context.getColor(R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(Round.floorTo(minBasal / 1.1, 0.5))
        viewport.setMaxY(Round.ceilTo(maxBasal * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }
}