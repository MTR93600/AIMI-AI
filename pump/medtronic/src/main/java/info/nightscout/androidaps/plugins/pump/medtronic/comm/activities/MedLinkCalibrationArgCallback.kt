package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

class MedLinkCalibrationArgCallback(
    private val aapsLogger: AAPSLogger,
    private val medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin,
    private val medLinkMedtronicUtil: MedLinkMedtronicUtil,
    private val resourceHelper: ResourceHelper,
    private val rxBus: RxBus
) : BaseCallback<BgSync.BgHistory, Supplier<Stream<String>>>() {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<BgSync.BgHistory?> {
        aapsLogger.info(LTag.PUMPBTCOMM, "calibration")
        val readings: BgSync.BgHistory? = parseAnswer(answer)
        return MedLinkStandardReturn(answer,readings)
    }

    private fun parseAnswer(
        answer: Supplier<Stream<String>>
    ): BgSync.BgHistory {
        val answers = answer.get().iterator()
        val ans = answer.get().collect(Collectors.joining())
        aapsLogger.info(LTag.PUMPBTCOMM, ans)

        val calibration: MutableList<BgSync.BgHistory.Calibration> = mutableListOf()
        while (answers.hasNext()) {
            val line = answers.next()
            if (line.contains("calibration not performed")) {
                medLinkMedtronicUtil.sendNotification(
                    MedtronicNotificationType.CalibrationFailed,
                    resourceHelper, rxBus
                )
                break;
            }
            if (line.contains("calibration confirmed from pump")) {
                medLinkMedtronicUtil.sendNotification(
                    MedtronicNotificationType.CalibrationSuccess,
                    resourceHelper, rxBus
                )
                break

            }
            if (line.contains("cal")) {
                val pattern = Pattern.compile("\\d+")
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    val bg = matcher.group().toDouble()
                    calibration.add(
                        BgSync.BgHistory.Calibration(
                            System.currentTimeMillis(),
                            bg,
                            medLinkMedtronicPumpPlugin.glucoseUnit()
                        )
                    )
                }
            }
        }
        return BgSync.BgHistory(emptyList(), calibration)
    }
}