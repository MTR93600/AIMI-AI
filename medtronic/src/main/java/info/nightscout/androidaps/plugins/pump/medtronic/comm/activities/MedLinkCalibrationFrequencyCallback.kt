package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.data.InMemoryMedLinkConfig
import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.interfaces.MedLinkSync
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import java.util.function.Supplier
import java.util.stream.Stream

class MedLinkCalibrationFrequencyCallback(private val aapsLogger: AAPSLogger,
private val medLinkMedtronicPumpPlugin: MedLinkMedtronicPumpPlugin
) : BaseCallback<InMemoryMedLinkConfig?, Supplier<Stream<String>>>() {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<InMemoryMedLinkConfig?> {
        aapsLogger.info(LTag.PUMPBTCOMM, "frequency calibration")
        val readings: InMemoryMedLinkConfig? = parseAnswer(answer)
        return MedLinkStandardReturn(answer,readings)
    }

    private fun parseAnswer(answer: Supplier<Stream<String>>): InMemoryMedLinkConfig? {
        var next = false
        var config: InMemoryMedLinkConfig? = null
        var calibration = -10
        answer.get().map {
            if (next) {
                calibration = it.toString().toInt()
                next = false
            }
            if (it.contains("-9 to +9")) {
                next = true
            }
            if (it.contains("medlink calibrated successful")) {
                config = InMemoryMedLinkConfig(System.currentTimeMillis(), calibration)
            }
        }
        return config
    }
}