package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCalibrateCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.function.Supplier
import java.util.stream.Stream

open class StartStopMessage<B>(
    bolusCommand: MedLinkCommandType,
    bleBolusCommand: BleCommand?,
    val postCommands: List<MedLinkPumpMessage<PumpDriverState>>,
    val shouldBeSuspended: Boolean
) : MedLinkPumpMessage<B>(bolusCommand, bleBolusCommand) {

    constructor(command:MedLinkCommandType,
                argCommand:MedLinkCommandType,
                baseCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
                argCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
                btSleepTime: Long,
                bleCommand: BleCalibrateCommand,
                postCommands: List<MedLinkPumpMessage<PumpDriverState>>,
                shouldBeSuspended: Boolean
    ): this(command, bleCommand, postCommands,shouldBeSuspended){
        this.argument = argCommand
        this.argCallback = argCallback
        this.baseCallback = baseCallback
        this.btSleepTime = btSleepTime
    }
}