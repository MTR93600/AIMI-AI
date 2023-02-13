package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.interfaces.BgSync.BgHistory
import info.nightscout.androidaps.interfaces.BgSync.BgHistory.BgValue
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.pump.common.data.MedLinkPumpStatus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Stream
import kotlin.streams.toList

/**
 * Created by Dirceu on 24/01/21.
 */
class BGHistoryCallback(
    private val medLinkPumpPlugin: MedLinkMedtronicPumpPlugin,
    private val aapsLogger: AAPSLogger,
    private val handleBG: Boolean,
    private val isCalibration: Boolean
) : BaseCallback<BgHistory, Supplier<Stream<String>>>() {

    var history: BgHistory? = null

    private inner class InvalidBGHistoryException constructor(message: String?) : RuntimeException(message)

    override fun apply(ans: Supplier<Stream<String>>): MedLinkStandardReturn<BgHistory> {
        val state = parseAnswer(ans)
        aapsLogger.info(LTag.PUMPBTCOMM,"applying history")
        if (handleBG && !isCalibration) {
            medLinkPumpPlugin.handleNewSensorData(state.first)
        }
        this.history = state.first
        if (isCalibration && state.second) {
            val list = ans.get().toList().toMutableList()
            list.add(MedLinkConst.FREQUENCY_CALIBRATION_SUCCESS)
            return MedLinkStandardReturn({ list.stream() }, state.first)
        }
        return MedLinkStandardReturn(ans, state.first)
    }

    private fun parseAnswer(ans: Supplier<Stream<String>>): Pair<BgHistory, Boolean> {
        val answers = ans.get()
        var calibration = true
        return try {
            val calibrations: MutableList<BgHistory.Calibration> = mutableListOf()
            val bgValues: MutableList<BgValue> = mutableListOf()

            answers.forEach { f: String ->
                val bgLinePattern = Pattern.compile("[bg|cl]:\\s?\\d{2,3}\\s+\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}")
                val matcher = bgLinePattern.matcher(f)
                //BG: 68 15:35 00‑00‑2000

                if ((f.length == 25 || f.length == 26) && matcher.find()) {
                    val data = matcher.group(0)

//                Double bg = Double.valueOf(data.substring(3, 6).trim());
                    val bgPat = Pattern.compile("\\d{2,3}")
                    assert(data != null)
                    val bgMatcher = bgPat.matcher(data)
                    bgMatcher.find()
                    val bg = java.lang.Double.valueOf(Objects.requireNonNull(bgMatcher.group(0)))
                    val datePattern = "HH:mm dd-MM-yyyy"
                    val dtPattern = Pattern.compile("\\d{1,2}:\\d{2}\\s+\\d{2}-\\d{2}-\\d{4}")
                    val dtMatcher = dtPattern.matcher(data)
                    dtMatcher.find()
                    val formatter = SimpleDateFormat(datePattern, Locale.getDefault())
                    var bgDate: Date? = null

                    try {
                        bgDate = formatter.parse(Objects.requireNonNull(dtMatcher.group(0)))
                        val firstDate = Date()
                        firstDate.time = 0L
                        assert(bgDate != null)
                        if (bgDate.time > System.currentTimeMillis()) {
                            throw InvalidBGHistoryException("TimeInFuture")
                        }
                        //                    aapsLogger.info(LTag.PUMPBTCOMM, f);
                        if (f.trim { it <= ' ' }.startsWith("cl:")) {
                            calibrations.add(
                                BgHistory.Calibration(
                                    bgDate.time, bg,
                                    medLinkPumpPlugin.glucoseUnit()
                                )
                            )
                        } else {
                            if (bgDate.toInstant().isAfter(Date().toInstant().minus(Duration.ofDays(3))) && bgDate.toInstant().isBefore(Date().toInstant().plus(Duration.ofMinutes(5)))) {
                                bgValues.add(
                                    BgValue(
                                        bgDate.time, 0.0, bg,
                                        0.0,
                                        BgSync.BgArrow.NONE, BgSync.SourceSensor.MM_ENLITE, null, null,
                                        null
                                    )
                                )
                            }
                        }
                    } catch (e: ParseException) {
                        e.printStackTrace()

                    }

                } else if ((f.contains("bg:") || f.contains("cl:")) && (f.length < 25 || f.length > 26)) {
                    calibration = false
                }
            }
            bgValues.sortBy { it.timestamp }
            // val sorted = bgs.sorted { b: BGHistory, a: BGHistory -> (b.timestamp - a.timestamp).toInt() }
            // val history = BGHistoryAccumulator()
            // sorted.forEachOrdered { f: BGHistory ->
            //     if (history.last != null) {
            //         f.lastBG = history.last!!.currentBG
            //         f.lastBGDate = history.last!!.lastBGDate
            //     }
            //     history.addBG(f)
            // }
            // val result = Supplier {
            //     history.acc.stream().map { f: BGHistory ->
            //         EnliteInMemoryGlucoseValue(
            //             f.currentBGDate.time, f.currentBG, false,
            //             f.lastBGDate.time, f.lastBG
            //         )
            //     }
            // }

            if (bgValues.isNotEmpty() && !isCalibration) {
                medLinkPumpPlugin.pumpStatusData.lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS
            }
            Pair(BgHistory(bgValues, calibrations), calibration)
        } catch (e: InvalidBGHistoryException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Invalid bg history reading")
            Pair(BgHistory(emptyList<BgValue>(), emptyList<BgHistory.Calibration>()), false)
        }
    }

}