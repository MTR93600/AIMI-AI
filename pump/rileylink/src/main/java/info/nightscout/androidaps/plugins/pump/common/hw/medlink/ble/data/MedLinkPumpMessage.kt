package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 25/09/20.
 */
open class MedLinkPumpMessage<B, C> //implements RLMessage
{



    fun firstFunction(): Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>> {
        return commands[0].parseFunction
    }

    fun firstCommand(): MedLinkCommandType {
        return commands[0].command
    }

    fun contains(commandType: MedLinkCommandType): Boolean {
        return commands.any { it.command == commandType }
    }

    var commands: MutableList<CommandStructure<B, BleCommand>> = listOf<CommandStructure<
        B, BleCommand>>().toMutableList()
    var supplementalCommands: MutableList<CommandStructure<C, BleCommand>> =
        listOf<CommandStructure<C, BleCommand>>().toMutableList()

    // // val commandType: MedLinkCommandType
    // var argCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>? = null
    //     protected set
    // var argument: MedLinkCommandType
    //     protected set
    @JvmField var baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>? = null
    var btSleepTime = 0L

    constructor(commandType: MedLinkCommandType, bleCommand: BleCommand) {
        this.commands = mutableListOf(CommandStructure(commandType, Optional.empty(), Optional.of(bleCommand), commandType.getRaw()))

    }

    // constructor(
    //     commandType: MedLinkCommandType,
    //     baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
    //     btSleepTime: Long
    // ) {
    //     this.commands = mutableListOf(Triple(commandType, optional(baseCallback),Optional.of(bleCommand)))
    //     this.btSleepTime = btSleepTime
    // }

    constructor(
        commandType: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepTime: Long,
        bleCommand: BleCommand,
        commandPriority: CommandPriority
    ) {
        this.commands = mutableListOf(CommandStructure(commandType, optional(baseCallback), Optional.of(bleCommand), commandType.getRaw()))
        this.btSleepTime = btSleepTime
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepTime: Long,
        bleCommand: BleCommand,
        commandPriority: CommandPriority
    ): this(commandType, baseCallback, btSleepTime, bleCommand,commandPriority) {
        if (argument != MedLinkCommandType.NoCommand) {
            commands.add(
                CommandStructure(argument, Optional.empty(), Optional.of(bleCommand), argument.getRaw())
            )
        }
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        argCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepTime: Long, bleCommand: BleCommand
    ):this(commandType, baseCallback, btSleepTime, bleCommand,
           CommandPriority.NORMAL) {
        this.commands.add(
            CommandStructure(argument, Optional.of(argCallback), Optional.of(bleCommand), argument.getRaw())
        )

    }

    constructor(
        commands: MutableList<CommandStructure<
            B, BleCommand>>,
        btSleepTime: Long
    ) {
        this.commands = commands
        this.btSleepTime = btSleepTime
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        bleCommand: BleCommand
    ) {
        this.commands = mutableListOf(
            CommandStructure(commandType, Optional.empty(), Optional.of(bleCommand), commandType.getRaw()),
            CommandStructure(argument, Optional.empty(), Optional.of(bleCommand), argument.getRaw())
        )

    }

    open fun commandData(index: Int) = if (commands.size < index) {
        commands[index].command.getRaw()
    } else {
        ByteArray(0)
    }

    fun optional(
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
    ) = if (baseCallback == null) Optional.empty() else Optional.of(baseCallback)

    override fun toString(): String {
        return "MedLinkPumpMessage{" +
            "commands=" + commands.joinToString() +
            ", baseCallback=" + baseCallback +
            ", btSleepTime=" + btSleepTime +
            '}'
    }

    fun nextMessageCommands(): MutableList<CommandStructure<
        Optional<
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>>>, Optional<BleCommand>>>{
        return mutableListOf<CommandStructure<
            Optional<
                Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>>>, Optional<BleCommand>>>()
    }
    // fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
    //     bleCommand.characteristicChanged(answer, bleComm, lastCommand)
    // }
    //
    // fun apply(bleComm: MedLinkBLE?) {
    //     bleCommand.applyResponse(bleComm)
    // }
}