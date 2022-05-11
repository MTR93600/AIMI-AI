package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/04/21.
 */
abstract class CommandExecutor protected constructor(
    val commandList: List<
        Pair<
            MedLinkCommandType,
            Function<
                Supplier<Stream<String>>,
                MedLinkStandardReturn<*>
                >?
            >
        >, val aapsLogger: AAPSLogger
) : Runnable {

    private var commandPosition = 0
    private var functionPosition = 0
    private var currentCommand: MedLinkCommandType? = null
    var nrRetries = 0
        protected set

    protected constructor(commandType: MedLinkCommandType, aapsLogger: AAPSLogger) :
        this(
            listOf(
                Pair<
                    MedLinkCommandType,
                    Function<
                        Supplier<Stream<String>>,
                        MedLinkStandardReturn<*>
                        >?
                    >(
                    commandType, null
                )
            ), aapsLogger
        ) {

    }

    //
    //    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
    //        this.remainingBleCommand = remainingBleCommand;
    //    }
    fun contains(com: MedLinkCommandType?): Boolean {
        return com != null && commandList.any { it.first.isSameCommand(com) }
    }

    fun nextCommand(): MedLinkCommandType {
        return if (commandPosition >= commandList.size) {
            MedLinkCommandType.NoCommand
        } else {
            commandList[commandPosition].first
        }
    }

    fun nextFunction(): Function<Supplier<Stream<String>>, out MedLinkStandardReturn<*>?>? {
        return if (commandList[functionPosition].second != null) {
            currentCommand = commandList[functionPosition].first
            commandList[functionPosition].second?.compose {
                aapsLogger.info(
                    LTag.APS, "applied"
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
            commandList[commandPosition].first
        else MedLinkCommandType.NoCommand
    }

    open fun hasFinished(): Boolean {
        aapsLogger.info(LTag.PUMPBTCOMM, commandList.joinToString())
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandPosition)
        return (commandPosition >= commandList.size
            )
    }

    fun commandExecuted() {
        if (commandPosition == 0) {
            nrRetries++
        } else if (commandList[commandPosition].first != MedLinkCommandType.NoCommand) {
            nrRetries++
        }
        commandPosition += 1
    }

    open fun clearExecutedCommand() {
        commandPosition = 0
        functionPosition = 0
    }

    override fun toString(): String {
        return "CommandExecutor{" +
            "command=" + commandList.joinToString() +
            ", commandPosition=" + commandPosition +
            ", functionPosition=" + functionPosition +
            ", nrRetries=" + nrRetries +
            '}'
    }

    fun matches(ext: MedLinkPumpMessage<*>): Boolean {
        return (commandList == ext.commands)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CommandExecutor
        return !hasFinished() && !that.hasFinished() && commandList == other.commandList
    }

    override fun hashCode(): Int {
        return Objects.hash(commandList, aapsLogger, commandPosition, functionPosition, currentCommand, nrRetries)
    }

    protected fun nextCommandData(): ByteArray {
        return if (commandPosition < commandList.size) {
            commandList[commandPosition].first.raw
        } else {
            MedLinkCommandType.NoCommand.raw
        }
    }

    val isInitialized: Boolean
        get() = commandPosition > 0 || functionPosition > 0

}