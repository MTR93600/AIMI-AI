package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.BgSync.BgHistory
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by Dirceu on 15/04/21.
 */
class IsigHistoryCallback     //        BGHistoryCallback.BGHistoryAccumulator history = new BGHistoryCallback.BGHistoryAccumulator();
//        sorted.forEachOrdered(f -> {
//            if (history.last != null) {
//                f.lastBG = history.last.currentBG;
//                f.lastBGDate = history.last.lastBGDate;
//            }
//            history.addBG(f);
//        });
//
//        Supplier<Stream<BgReading>> result = () -> history.acc.stream().map(f -> {
//            return new BgReading(injector, f.currentBGDate.getTime(), f.currentBG, null,
//                    f.lastBGDate.getTime(), f.lastBG, f.source);
//
//        });
//        if (result.get().findFirst().isPresent()) {
//            medLinkPumpPlugin.getPumpStatusData().lastReadingStatus = MedLinkPumpStatus.BGReadingStatus.SUCCESS;
//    private EnliteInMemoryGlucoseValue getReading(BgSync.BgHistory bgReadingsList, int count, int delta) {
//        if (count + delta >= bgReadingsList.getBgValue().size()) {
//            return null;
//        } else return bgReadingsList[count + delta];
//    }
//
//        return result.get().toArray(BgReading[]::new);
    (
    private val injector: HasAndroidInjector,
    private val medLinkPumpPlugin: MedLinkMedtronicPumpPlugin,
    private val aapsLogger: AAPSLogger, private val handleBG: Boolean, private val bgHistoryCallback: BGHistoryCallback
) : BaseCallback<BgHistory, Supplier<Stream<String>>>() {

    override fun apply(ans: Supplier<Stream<String>>): MedLinkStandardReturn<BgHistory?> {
        aapsLogger.info(LTag.PUMPBTCOMM, "isig")
        val toParse = ans.get()
        aapsLogger.info(LTag.PUMPBTCOMM, "isig2")
        val readings: BgHistory? = parseAnswer(ans, bgHistoryCallback.history)
        if (readings != null) {
            medLinkPumpPlugin.handleNewSensorData(readings)
        }
        //        if (handleBG) {
//            medLinkPumpPlugin.handleNewBgData(readings);
//        }
        return if (readings != null && readings.bgValue.isNotEmpty()) {
            MedLinkStandardReturn({ toParse }, readings, mutableListOf())
        } else MedLinkStandardReturn({ toParse }, null, mutableListOf())
    }

    private fun parseAnswer(
        ans: Supplier<Stream<String>>,
        bgReadings: BgHistory?
    ): BgHistory? {
        aapsLogger.info(LTag.PUMPBTCOMM, "isig")
        val answers = ans.get().iterator()
        val answer = ans.get().collect(Collectors.joining())
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        var memAddress = -1
        //        answers.iterator();
        val isigs: MutableList<Double?> = ArrayList()
        while (answers.hasNext() && memAddress < 0) {
            val line = answers.next()
            if (line.contains("history page number")) {
                val memPatter = Pattern.compile("\\d{2,3}")
                val memMatcher = memPatter.matcher(line)
                if (memMatcher.find()) {
                    memAddress = memMatcher.group(0)?.toInt() ?: -1
                }
            }
        }
        while (answers.hasNext()) {
            val line = answers.next()
            if (line.contains("end of data")) {
                break
            }
        }
        while (answers.hasNext()) {
            val line = answers.next()
            val isigLinePattern = Pattern.compile("isig:\\s?\\d{1,3}\\.\\d{1,2}\\sna")
            val matcher = isigLinePattern.matcher(line)
            //BG: 68 15:35 00‑00‑2000
            if (line.length <= 15 && matcher.find()) {
                val data = matcher.group(0)
                //                Double bg = Double.valueOf(data.substring(3, 6).trim());
                val isigPat = Pattern.compile("\\d+\\.\\d+")
                val isigMatcher = isigPat.matcher(data)
                if (isigMatcher.find()) {
                    isigs.add(java.lang.Double.valueOf(isigMatcher.group(0)))
                }
            } else if (line.trim { it <= ' ' }.isNotEmpty() && line.trim { it <= ' ' } != "ready" && !line.contains("end of data") && !line.contains("beginning of data")) {
                aapsLogger.info(LTag.PUMPBTCOMM, "isig failed")
                aapsLogger.info(LTag.PUMPBTCOMM, "" + line.trim { it <= ' ' }.length)
                aapsLogger.info(LTag.PUMPBTCOMM, "" + matcher.find())
                aapsLogger.info(LTag.PUMPBTCOMM, "Invalid isig $line")
                // medLinkPumpPlugin.calibrateMedLinkFrequency()
                break
            }
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "isig")
        isigs.forEach(Consumer { f: Double? -> aapsLogger.info(LTag.PUMPBTCOMM, f.toString()) })
        isigs.reverse()
        val bfValue = bgReadings?.bgValue?.zip(isigs)?.map { pair ->
            pair.first.copy(isig = pair.second)
        }
        bgReadings?.bgValue = bfValue!!
        // val result: MutableList<EnliteInMemoryGlucoseValue?> = ArrayList()
        aapsLogger.info(LTag.PUMPBTCOMM, "isigs s" + isigs.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "readings s" + bgReadings.bgValue.size)
        return bgReadings
        //        if (isigs.size() == bgReadingsList.size()) {
        // var delta = 0
        // var count = 0
        // while (count < isigs.size) {
        //     var reading: EnliteInMemoryGlucoseValue? = getReading(bgReadings, count, delta) ?: break
        //     if (reading.source === info.nightscout.androidaps.plugins.pump.common.defs.PumpType.USER) {
        //         result.add(EnliteInMemoryGlucoseValue(reading, 0.0, 0.0))
        //         delta++
        //         reading = bgReadings.get(count + delta)
        //         if (reading == null) {
        //             break
        //         }
        //     }
        //     val isig = isigs[count]
        //     val calibrationFactor = 0.0
        //     result.add(EnliteInMemoryGlucoseValue(reading, isig!!, calibrationFactor))
        //     count++
        // }
//         return if (result.size > 0 && result[0] != null) {
//             aapsLogger.info(LTag.PUMPBTCOMM, "adding isigs")
//             //            medLinkPumpPlugin.handleNewSensorData(result);
//             result.toTypedArray()
//             //        }
// //        if (count + delta == result.length) {
// //            return result;
//         } else {
//             null
//         }
    } //          isigs.reverse
}