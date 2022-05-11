package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.data.InMemoryMedLinkConfig
import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCalibrateFrequencyCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import org.json.JSONObject
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

class CalibrateFrequencyMedLinkMessage(
    private val config: List<String>,
    baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<InMemoryMedLinkConfig>>,
    btSleepTime: Long,
    bleCommand: BleCalibrateFrequencyCommand,
    val calibrateVerificationMessage: MedLinkPumpMessage<Stream<JSONObject>>
) : MedLinkPumpMessage<InMemoryMedLinkConfig>(
    MedLinkCommandType.CalibrateFrequency,
    calibrationArgument,
    baseCallback,
    btSleepTime,
    bleCommand
) {

    override fun getArgumentData(): ByteArray? {
        calibrationArgument.config = config.iterator();
        return calibrationArgument.raw
    }

    companion object {
        val calibrationArgument = MedLinkCommandType.CalibrateFrequencyArgument
    }

}

