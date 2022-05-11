package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 25/09/20.
 */
open class MedLinkPumpMessage<B> //implements RLMessage
{

    var commands: MutableList<Pair<MedLinkCommandType, Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>>> = listOf<Pair<MedLinkCommandType,
        Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>>>().toMutableList()
    private var bleCommand: BleCommand

    // // val commandType: MedLinkCommandType
    // var argCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>? = null
    //     protected set
    // var argument: MedLinkCommandType
    //     protected set
    @JvmField var baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>? = null
    var btSleepTime = 0L

    constructor(commandType: MedLinkCommandType, bleCommand: BleCommand) {
        this.commands = mutableListOf(Pair(commandType, Optional.empty()))
        this.bleCommand = bleCommand
    }

    constructor(
        commandType: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
        btSleepTime: Long, bleCommand: BleCommand
    ) {
        this.commands = mutableListOf(Pair(commandType, optional(baseCallback)))
        this.btSleepTime = btSleepTime
        this.bleCommand = bleCommand
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
        btSleepTime: Long,
        bleCommand: BleCommand
    ) {
        this.commands = mutableListOf(
            Pair(commandType, optional(baseCallback)),
            Pair(argument, Optional.empty())
        )
        this.btSleepTime = btSleepTime
        this.bleCommand = bleCommand
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        argCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepTime: Long, bleCommand: BleCommand
    ) {
        this.commands = mutableListOf(
            Pair(commandType, Optional.of(baseCallback)),
            Pair(argument, Optional.of(argCallback))
        )
        this.btSleepTime = btSleepTime
        this.bleCommand = bleCommand
    }

    constructor(
        commands: MutableList<Pair<MedLinkCommandType,
            Optional<
                Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>>>,
        btSleepTime: Long, bleCommand: BleCommand
    ) {
        this.commands = commands
        this.btSleepTime = btSleepTime
        this.bleCommand = bleCommand
    }

    constructor(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        bleCommand: BleCommand
    ) {
        this.commands = mutableListOf(
            Pair(commandType, Optional.empty()),
            Pair(argument, Optional.empty())
        )

        this.bleCommand = bleCommand
    }

    fun commandData(index: Int) = if(commands.size < index) {
        commands[index].first.raw
    }else{
        ByteArray(0)
    }

    fun optional(
        baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>?,
    ) = if (baseCallback == null) Optional.empty() else Optional.of(baseCallback)

    override fun toString(): String {
        return "MedLinkPumpMessage{" +
            "commands=" + commands.joinToString()  +
            ", baseCallback=" + baseCallback +
            ", btSleepTime=" + btSleepTime +
            '}'
    }

    fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        bleCommand.characteristicChanged(answer, bleComm, lastCommand)
    }

    fun apply(bleComm: MedLinkBLE?) {
        bleCommand.applyResponse(bleComm)
    }
}