package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

open class StartStopMessage<B,C>(
    bolusCommand: MedLinkCommandType,
    bleBolusCommand: BleCommand,
    val shouldBeSuspended: Boolean,
    val argument: String
) : MedLinkPumpMessage<B, C>(bolusCommand, bleBolusCommand) {

    constructor(
        command: MedLinkCommandType,
        argCommand: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        argCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
        btSleepTime: Long,
        bleCommand: BleCommand,
        shouldBeSuspended: Boolean,
        argument: String
    ) : this(command, bleCommand, shouldBeSuspended, argument) {
        this.commands.clear()
        this.commands.add(
            CommandStructure(
                command, Optional.of(baseCallback), Optional.of(bleCommand), command.getRaw()
            )
        )
        this.btSleepTime = btSleepTime
        val arg = if (argCallback != null) {
            Optional.of(argCallback)
        } else {
            Optional.empty()
        }
        this.commands.add(
            CommandStructure(
                argCommand, arg, Optional.empty(), argCommand.getRaw(argument)
            )
        )
    }

}