package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

class CommandStructure<B, C>(val command: MedLinkCommandType, val parseFunction: Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>, val commandHandler: Optional<C>, val
commandArgument:ByteArray, var commandPriority: CommandPriority = CommandPriority.NORMAL,
                             val errorFunction: Optional<Function<MedLinkStandardReturn<B>, Unit>> = Optional.empty()
) {
    constructor(first: MedLinkCommandType,
                second: Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>,
                third: Optional<C>, commandPriority: CommandPriority,

                errorFunction: Optional<Function<MedLinkStandardReturn<B>, Unit>> = Optional.empty()) :
        this(first, second, third, first.getRaw(),commandPriority, errorFunction)

    override fun toString(): String {
        return "CommandStructure(command=$command, parseFunction=$parseFunction, commandHandler=$commandHandler, commandArgument=${String(commandArgument, Charsets.UTF_8)})"
    }

}