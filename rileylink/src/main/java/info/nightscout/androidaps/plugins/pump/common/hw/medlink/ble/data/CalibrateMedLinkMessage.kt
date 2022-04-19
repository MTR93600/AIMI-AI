package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCalibrateCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.function.Supplier
import java.util.stream.Stream

class CalibrateMedLinkMessage(
    private val calibrationInfo: Double,
    baseCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
    argCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
    btSleepTime: Long,
    bleCommand: BleCalibrateCommand,
    shouldBeSuspended: Boolean,
    startStopCommand:List<MedLinkPumpMessage<PumpDriverState>>
) : StartStopMessage<BgSync.BgHistory>(
    MedLinkCommandType.Calibrate,
    Companion.calibrationArgument,
    baseCallback,
    argCallback,
    btSleepTime,
    bleCommand,
    startStopCommand,
    shouldBeSuspended
) {

    private val calibrationArgument = MedLinkCommandType.CalibrateValue

    override fun getArgumentData(): ByteArray? {
        calibrationArgument.bgValue = calibrationInfo
        return calibrationArgument.raw
    }

    companion object {
        val calibrationArgument = MedLinkCommandType.CalibrateValue
    }
}

