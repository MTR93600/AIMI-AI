package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.CommandStructure
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/04/21.
 */
abstract class CommandExecutor<B> protected constructor(
    val commandList: MutableList<CommandStructure<B, BleCommand>>,
    val aapsLogger: AAPSLogger,
) : Runnable {

    var isConfirmed: Boolean = false
    private var commandPosition = 0
    private var functionPosition = 0
    private var currentCommand: MedLinkCommandType? = null
    var nrRetries = 0
        protected set

    protected constructor (commandType: MedLinkCommandType, aapsLogger: AAPSLogger) :
        this(
            mutableListOf(
                CommandStructure(
                    commandType, Optional.empty(),
                    Optional.empty(),
                    CommandPriority.HIGH
                )
            ), aapsLogger
        )

    //
    //    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
    //        this.remainingBleCommand = remainingBleCommand;
    //    }
    fun contains(com: MedLinkCommandType?): Boolean {
        return com != null && commandList.any { it.command.isSameCommand(com) }
    }

    fun nextCommand(): MedLinkCommandType {
        return if (commandPosition >= commandList.size) {
            MedLinkCommandType.NoCommand
        } else {
            commandList[commandPosition].command
        }
    }

    fun nextRaw(): ByteArray {
        synchronized(commandPosition) {
            return try {
                if (commandPosition >= commandList.size) {
                    MedLinkCommandType.NoCommand.getRaw()
                } else {
                    commandList[commandPosition].commandArgument
                }
            } catch (e: ArrayIndexOutOfBoundsException) {
                aapsLogger.info(LTag.PUMPBTCOMM, "exception at getting next raw $commandPosition")
                val it = commandList.iterator()
                val buf = StringBuffer()
                while (it.hasNext()) {
                    buf.append(it.next().toString())
                    buf.append("\n")
                }
                aapsLogger.info(LTag.PUMPBTCOMM, commandList.joinToString { buf.toString() })
                return MedLinkCommandType.NoCommand.getRaw()
            }
        }
    }

    fun nextFunction(): Function<Supplier<Stream<String>>, out MedLinkStandardReturn<*>?>? {
        return if (functionPosition < commandList.size && commandList[functionPosition].parseFunction.isPresent) {
            currentCommand = commandList[functionPosition].command
            commandList[functionPosition].parseFunction.get().compose {
                aapsLogger.info(
                    LTag.APS, "applied $functionPosition"
                )
                functionPosition += 1
                it
            }

            // } else if (functionPosition == 1 && medLinkPumpMessage.argCallback != null) {
            //     currentCommand = medLinkPumpMessage.argument
            //     medLinkPumpMessage.argCallback.compose {
            //         functionPosition += 1
            //         it
            //     }
            // } else if (functionPosition == 1 && medLinkPumpMessage is BasalMedLinkMessage) {
            //     medLinkPumpMessage.profileCallback.compose {
            //         functionPosition += 1
            //         it
            //     }
        } else null
    }

    fun getCurrentCommand(): MedLinkCommandType {
        return if (commandPosition < commandList.size)
            commandList[commandPosition].command
        else MedLinkCommandType.NoCommand
    }

    open fun hasFinished(): Boolean {
        aapsLogger.info(LTag.PUMPBTCOMM, commandList.joinToString())
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandPosition)
        return (commandPosition >= commandList.size || nextCommand() == MedLinkCommandType.NoCommand
            )
    }

    fun commandExecuted() {
        if (commandPosition == 0) {
            nrRetries++
        } else if (commandPosition < commandList.size && commandList[commandPosition].command != MedLinkCommandType.NoCommand) {
            nrRetries++
        }
        commandPosition += 1

    }

    open fun clearExecutedCommand() {
        commandPosition = 0
        functionPosition = 0
        isConfirmed = false
    }

    override fun toString(): String {
        return "CommandExecutor{" +
            "command=" + commandList.joinToString() +
            ", commandPosition=" + commandPosition +
            ", functionPosition=" + functionPosition +
            ", nrRetries=" + nrRetries +
            ", confirmed=" + isConfirmed +

            '}'
    }

    fun matches(
        commandList: MutableList<
            Pair<
                MedLinkCommandType,
                Optional<Function<
                    Supplier<Stream<String>>,
                    MedLinkStandardReturn<*>
                    >?
                    >>
            >,
    ): Boolean {
        return (commandList == this.commandList)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CommandExecutor<*>
        return !hasFinished() && !that.hasFinished() && commandList == other.commandList
    }

    override fun hashCode(): Int {
        return Objects.hash(commandList, aapsLogger, commandPosition, functionPosition, currentCommand, nrRetries)
    }

    protected fun nextCommandData(): ByteArray {
        return if (commandPosition < commandList.size) {
            commandList[commandPosition].commandArgument
        } else {
            MedLinkCommandType.NoCommand.getRaw()
        }
    }

    fun firstCommand(): MedLinkCommandType {
        return commandList[0].command
    }

    fun secondCommand(): MedLinkCommandType {
        return if (commandList.size > 1) commandList[1].command else MedLinkCommandType.NoCommand
    }

    fun nextBleCommand(): Optional<out BleCommand> {
        return if (commandList.size > functionPosition) commandList[functionPosition].commandHandler
        else commandList[commandList.size - 1].commandHandler

    }

    val isInitialized: Boolean
        get() = commandPosition > 0 || functionPosition > 0

    override fun run() {
        isConfirmed = false
    }

    fun commandFailed() {

    }
}