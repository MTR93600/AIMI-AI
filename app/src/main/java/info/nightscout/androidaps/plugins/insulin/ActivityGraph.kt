package info.nightscout.androidaps.plugins.insulin

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.androidaps.utils.T
import java.util.*
import kotlin.math.floor

class ActivityGraph : GraphView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun show(insulin: Insulin) {
        removeAllSeries()
        mSecondScale = null
        val hours = floor(insulin.dia + 1).toLong()
        val bolus = Bolus(
            timestamp = 0,
            amount = 1.0,
            type = Bolus.Type.NORMAL
        )
        val activityArray: MutableList<DataPoint> = ArrayList()
        val iobArray: MutableList<DataPoint> = ArrayList()
        var time: Long = 0
        while (time <= T.hours(hours).msecs()) {
            val iob = insulin.iobCalcForTreatment(bolus, time, insulin.dia)
            activityArray.add(DataPoint(T.msecs(time).mins().toDouble(), iob.activityContrib))
            iobArray.add(DataPoint(T.msecs(time).mins().toDouble(), iob.iobContrib))
            time += T.mins(5).msecs()
        }
        addSeries(LineGraphSeries(Array(activityArray.size) { i -> activityArray[i] }).also {
            it.thickness = 8
            gridLabelRenderer.verticalLabelsColor = it.color
        })
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX((hours * 60).toDouble())
        gridLabelRenderer.numHorizontalLabels = (hours + 1).toInt()
        gridLabelRenderer.horizontalAxisTitle = "[min]"
        secondScale.addSeries(LineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.color = Color.MAGENTA
            it.backgroundColor = Color.argb(70, 255, 0, 255)
        })
        secondScale.minY = 0.0
        secondScale.maxY = 1.0
        gridLabelRenderer.verticalLabelsSecondScaleColor = Color.MAGENTA
    }
}