package info.nightscout.androidaps.watchfaces.utils

import android.graphics.DashPathEffect
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import info.nightscout.shared.weardata.EventData.SingleBg
import info.nightscout.shared.weardata.EventData.TreatmentData.Basal
import lecho.lib.hellocharts.model.Axis
import lecho.lib.hellocharts.model.AxisValue
import lecho.lib.hellocharts.model.Line
import lecho.lib.hellocharts.model.LineChartData
import lecho.lib.hellocharts.model.PointValue
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class BgGraphBuilder(
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val bgDataList: List<SingleBg>,
    private val predictionsList: List<SingleBg>,
    private val tempWatchDataList: List<EventData.TreatmentData.TempBasal>,
    private val basalWatchDataList: ArrayList<Basal>,
    private val bolusWatchDataList: ArrayList<EventData.TreatmentData.Treatment>,
    private val pointSize: Int,
    private val highColor: Int,
    private val lowColor: Int,
    private val midColor: Int,
    private val gridColour: Int,
    private val basalBackgroundColor: Int,
    private val basalCenterColor: Int,
    private val bolusInvalidColor: Int,
    private val carbsColor: Int,
    private val timeSpan: Int
) {

    private val predictionEndTime: Long
    private var endingTime: Long = System.currentTimeMillis() + 1000L * 60 * 6 * timeSpan
    private var startingTime: Long = System.currentTimeMillis() - 1000L * 60 * 60 * timeSpan
    private var fuzzyTimeDiv = (1000 * 60 * 1).toDouble()
    private var highMark: Double = bgDataList[bgDataList.size - 1].high
    private var lowMark: Double = bgDataList[bgDataList.size - 1].low
    private val inRangeValues: MutableList<PointValue> = ArrayList()
    private val highValues: MutableList<PointValue> = ArrayList()
    private val lowValues: MutableList<PointValue> = ArrayList()

    init {
        predictionEndTime = getPredictionEndTime()
        endingTime = max(predictionEndTime, endingTime)
    }

    //used for low resolution screen.
    constructor(
        sp: SP, dateUtil: DateUtil,
        aBgList: List<SingleBg>,
        predictionsList: List<SingleBg>,
        tempWatchDataList: List<EventData.TreatmentData.TempBasal>,
        basalWatchDataList: ArrayList<Basal>,
        bolusWatchDataList: ArrayList<EventData.TreatmentData.Treatment>,
        aPointSize: Int, aMidColor: Int, gridColour: Int, basalBackgroundColor: Int, basalCenterColor: Int, bolusInvalidColor: Int, carbsColor: Int, timeSpan: Int
    ) : this(
        sp, dateUtil,
        aBgList, predictionsList, tempWatchDataList, basalWatchDataList,
        bolusWatchDataList, aPointSize, aMidColor, aMidColor, aMidColor, gridColour,
        basalBackgroundColor, basalCenterColor, bolusInvalidColor, carbsColor, timeSpan
    )

    fun lineData(): LineChartData {
        val lineData = LineChartData(defaultLines())
        lineData.axisYLeft = yAxis()
        lineData.axisXBottom = xAxis()
        return lineData
    }

    private fun defaultLines(): List<Line> {
        addBgReadingValues()
        val lines: MutableList<Line> = ArrayList()
        lines.add(highLine())
        lines.add(lowLine())
        lines.add(inRangeValuesLine())
        lines.add(lowValuesLine())
        lines.add(highValuesLine())
        var minChart = lowMark
        var maxChart = highMark
        for ((_, _, _, _, _, _, _, sgv) in bgDataList) {
            if (sgv > maxChart) maxChart = sgv
            if (sgv < minChart) minChart = sgv
        }
        var maxBasal = 0.1
        for ((_, _, amount) in basalWatchDataList) {
            if (amount > maxBasal) maxBasal = amount
        }
        var maxTemp = maxBasal
        for ((_, _, _, _, amount) in tempWatchDataList) {
            if (amount > maxTemp) maxTemp = amount
        }
        var factor = (maxChart - minChart) / maxTemp
        // in case basal is the highest, don't paint it totally at the top.
        factor = min(factor, (maxChart - minChart) / maxBasal * (2 / 3.0))
        val highlight = sp.getBoolean(R.string.key_highlight_basals, false)
        for (twd in tempWatchDataList) {
            if (twd.endTime > startingTime) {
                lines.add(tempValuesLine(twd, minChart.toFloat(), factor, false, if (highlight) pointSize + 1 else pointSize))
                if (highlight) lines.add(tempValuesLine(twd, minChart.toFloat(), factor, true, 1))
            }
        }
        addPredictionLines(lines)
        lines.add(basalLine(minChart.toFloat(), factor, highlight))
        lines.add(bolusLine(minChart.toFloat()))
        lines.add(bolusInvalidLine(minChart.toFloat()))
        lines.add(carbsLine(minChart.toFloat()))
        lines.add(smbLine(minChart.toFloat()))
        return lines
    }

    private fun basalLine(offset: Float, factor: Double, highlight: Boolean): Line {
        val pointValues: MutableList<PointValue> = ArrayList()
        for ((startTime, endTime, amount) in basalWatchDataList) {
            if (endTime > startingTime) {
                val begin = max(startingTime, startTime)
                pointValues.add(PointValue(fuzz(begin), offset + (factor * amount).toFloat()))
                pointValues.add(PointValue(fuzz(endTime), offset + (factor * amount).toFloat()))
            }
        }
        return Line(pointValues).also { basalLine ->
            basalLine.setHasPoints(false)
            basalLine.color = basalCenterColor
            basalLine.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 4f)
            basalLine.strokeWidth = if (highlight) 2 else 1
        }
    }

    private fun bolusLine(offset: Float): Line {
        val pointValues: MutableList<PointValue> = ArrayList()
        for ((date, bolus, _, isSMB, isValid) in bolusWatchDataList) {
            if (date in (startingTime + 1)..endingTime && !isSMB && isValid && bolus > 0) {
                pointValues.add(PointValue(fuzz(date), offset - 2))
            }
        }
        return Line(pointValues).also { line ->
            line.color = basalCenterColor
            line.setHasLines(false)
            line.pointRadius = pointSize * 2
            line.setHasPoints(true)
        }
    }

    private fun smbLine(offset: Float): Line {
        val pointValues: MutableList<PointValue> = ArrayList()
        for ((date, bolus, _, isSMB, isValid) in bolusWatchDataList) {
            if (date in (startingTime + 1)..endingTime && isSMB && isValid && bolus > 0) {
                pointValues.add(PointValue(fuzz(date), offset - 2))
            }
        }
        return Line(pointValues).also { line ->
            line.color = basalCenterColor
            line.setHasLines(false)
            line.pointRadius = pointSize
            line.setHasPoints(true)
        }
    }

    private fun bolusInvalidLine(offset: Float): Line {
        val pointValues: MutableList<PointValue> = ArrayList()
        for ((date, bolus, carbs, _, isValid) in bolusWatchDataList) {
            if (date in (startingTime + 1)..endingTime && !(isValid && (bolus > 0 || carbs > 0))) {
                pointValues.add(PointValue(fuzz(date), offset - 2))
            }
        }
        return Line(pointValues).also { line ->
            line.color = bolusInvalidColor
            line.setHasLines(false)
            line.pointRadius = pointSize
            line.setHasPoints(true)
        }
    }

    private fun carbsLine(offset: Float): Line {
        val pointValues: MutableList<PointValue> = ArrayList()
        for ((date, _, carbs, isSMB, isValid) in bolusWatchDataList) {
            if (date in (startingTime + 1)..endingTime && !isSMB && isValid && carbs > 0) {
                pointValues.add(PointValue(fuzz(date), offset + 2))
            }
        }
        return Line(pointValues).also { line ->
            line.color = carbsColor
            line.setHasLines(false)
            line.pointRadius = pointSize * 2
            line.setHasPoints(true)
        }
    }

    private fun addPredictionLines(lines: MutableList<Line>) {
        val values: MutableMap<Int, MutableList<PointValue>> = HashMap()
        val endTime = getPredictionEndTime()
        for ((timeStamp, _, _, _, _, _, _, sgv, _, _, color) in predictionsList) {
            if (timeStamp <= endTime) {
                val value = min(sgv, UPPER_CUTOFF_SGV)
                if (!values.containsKey(color)) {
                    values[color] = ArrayList()
                }
                values[color]!!.add(PointValue(fuzz(timeStamp), value.toFloat()))
            }
        }
        for ((key, value) in values) {
            val line = Line(value)
            line.color = key
            line.setHasLines(false)
            var size = pointSize / 2
            size = if (size > 0) size else 1
            line.pointRadius = size
            line.setHasPoints(true)
            lines.add(line)
        }
    }

    private fun highValuesLine(): Line =
        Line(highValues).also { highValuesLine ->
            highValuesLine.color = highColor
            highValuesLine.setHasLines(false)
            highValuesLine.pointRadius = pointSize
            highValuesLine.setHasPoints(true)
        }

    private fun lowValuesLine(): Line =
        Line(lowValues).also { lowValuesLine ->
            lowValuesLine.color = lowColor
            lowValuesLine.setHasLines(false)
            lowValuesLine.pointRadius = pointSize
            lowValuesLine.setHasPoints(true)
        }

    private fun inRangeValuesLine(): Line =
        Line(inRangeValues).also { inRangeValuesLine ->
            inRangeValuesLine.color = midColor
            inRangeValuesLine.pointRadius = pointSize
            inRangeValuesLine.setHasPoints(true)
            inRangeValuesLine.setHasLines(false)
        }

    private fun tempValuesLine(twd: EventData.TreatmentData.TempBasal, offset: Float, factor: Double, isHighlightLine: Boolean, strokeWidth: Int): Line {
        val lineValues: MutableList<PointValue> = ArrayList()
        val begin = max(startingTime, twd.startTime)
        lineValues.add(PointValue(fuzz(begin), offset + (factor * twd.startBasal).toFloat()))
        lineValues.add(PointValue(fuzz(begin), offset + (factor * twd.amount).toFloat()))
        lineValues.add(PointValue(fuzz(twd.endTime), offset + (factor * twd.amount).toFloat()))
        lineValues.add(PointValue(fuzz(twd.endTime), offset + (factor * twd.endBasal).toFloat()))
        return Line(lineValues).also { valueLine ->
            valueLine.setHasPoints(false)
            if (isHighlightLine) {
                valueLine.color = basalCenterColor
                valueLine.strokeWidth = 1
            } else {
                valueLine.color = basalBackgroundColor
                valueLine.strokeWidth = strokeWidth
            }
        }
    }

    private fun addBgReadingValues() {
        for ((timeStamp, _, _, _, _, _, _, sgv) in bgDataList) {
            if (timeStamp > startingTime) {
                when {
                    sgv >= 450      -> highValues.add(PointValue(fuzz(timeStamp), 450.toFloat()))
                    sgv >= highMark -> highValues.add(PointValue(fuzz(timeStamp), sgv.toFloat()))
                    sgv >= lowMark  -> inRangeValues.add(PointValue(fuzz(timeStamp), sgv.toFloat()))
                    sgv >= 40       -> lowValues.add(PointValue(fuzz(timeStamp), sgv.toFloat()))
                    sgv >= 11       -> lowValues.add(PointValue(fuzz(timeStamp), 40.toFloat()))
                }
            }
        }
    }

    private fun highLine(): Line {
        val highLineValues: MutableList<PointValue> = ArrayList()
        highLineValues.add(PointValue(fuzz(startingTime), highMark.toFloat()))
        highLineValues.add(PointValue(fuzz(endingTime), highMark.toFloat()))
        return Line(highLineValues).also { highLine ->
            highLine.setHasPoints(false)
            highLine.strokeWidth = 1
            highLine.color = highColor
        }
    }

    private fun lowLine(): Line {
        val lowLineValues: MutableList<PointValue> = ArrayList()
        lowLineValues.add(PointValue(fuzz(startingTime), lowMark.toFloat()))
        lowLineValues.add(PointValue(fuzz(endingTime), lowMark.toFloat()))
        return Line(lowLineValues).also { lowLine ->
            lowLine.setHasPoints(false)
            lowLine.color = lowColor
            lowLine.strokeWidth = 1
        }
    }

    /////////AXIS RELATED//////////////
    private fun yAxis(): Axis =
        Axis().also { yAxis ->
            yAxis.isAutoGenerated = true
            val axisValues: List<AxisValue> = ArrayList()
            yAxis.values = axisValues
            yAxis.setHasLines(false)
            yAxis.lineColor = gridColour
        }

    private fun xAxis(): Axis {
        val timeNow = System.currentTimeMillis()
        val xAxis = Axis()
        xAxis.isAutoGenerated = false
        val xAxisValues: MutableList<AxisValue> = ArrayList()

        //get the time-tick at the full hour after start_time
        val startGC = GregorianCalendar()
        startGC.timeInMillis = startingTime
        startGC[Calendar.MILLISECOND] = 0
        startGC[Calendar.SECOND] = 0
        startGC[Calendar.MINUTE] = 0
        startGC.add(Calendar.HOUR, 1)

        //Display current time on the graph
        xAxisValues.add(AxisValue(fuzz(timeNow)).setLabel(dateUtil.timeString(timeNow)))
        var hourTick = startGC.timeInMillis

        // add all full hours within the timeframe
        while (hourTick < endingTime) {
            if (abs(hourTick - timeNow) > 8 * (endingTime - startingTime) / 60) {
                xAxisValues.add(AxisValue(fuzz(hourTick)).setLabel(dateUtil.hourString(hourTick)))
            } else {
                //don't print hour label if too close to now to avoid overlaps
                xAxisValues.add(AxisValue(fuzz(hourTick)).setLabel(""))
            }

            //increment by one hour
            hourTick += (60 * 60 * 1000).toLong()
        }
        xAxis.values = xAxisValues
        xAxis.textSize = 10
        xAxis.setHasLines(true)
        xAxis.lineColor = gridColour
        xAxis.textColor = gridColour
        return xAxis
    }

    private fun getPredictionEndTime(): Long {
        var maxPredictionDate = System.currentTimeMillis()
        for ((timeStamp) in predictionsList) {
            if (maxPredictionDate < timeStamp) {
                maxPredictionDate = timeStamp
            }
        }
        return min(maxPredictionDate.toDouble(), System.currentTimeMillis() + MAX_PREDICTION__TIME_RATIO * timeSpan * 1000 * 60 * 60).toLong()
    }

    private fun fuzz(value: Long): Float {
        return (value / fuzzyTimeDiv).roundToLong().toFloat()
    }

    companion object {

        const val MAX_PREDICTION__TIME_RATIO = 3.0 / 5
        const val UPPER_CUTOFF_SGV = 400.0
    }
}