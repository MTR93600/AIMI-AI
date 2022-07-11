package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

class CommandStructure<B, C>(val command: MedLinkCommandType, val parseFunction: Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>, val commandHandler: Optional<C>, val
commandArgument:
ByteArray) {
    constructor(first: MedLinkCommandType, second: Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>, third: Optional<C>) :
        this(first, second, third, first.getRaw())

    override fun toString(): String {
        return "CommandStructure(command=$command, parseFunction=$parseFunction, commandHandler=$commandHandler, commandArgument=${String(commandArgument, Charsets.UTF_8)})"
    }

}