package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/04/21.
 */
abstract class CommandExecutor protected constructor(val medLinkPumpMessage: MedLinkPumpMessage<*>, val aapsLogger: AAPSLogger) : Runnable {

    private var commandPosition = 0
    private var functionPosition = 0
    private var currentCommand: MedLinkCommandType? = null
    var nrRetries = 0
        protected set

    protected constructor(commandType: MedLinkCommandType?, aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData?) : this(
        MedLinkPumpMessage<Any>(
            commandType,
            BleCommand(aapsLogger, medLinkServiceData)
        ), aapsLogger
    ) {
    }

    //
    //    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
    //        this.remainingBleCommand = remainingBleCommand;
    //    }
    operator fun contains(com: MedLinkCommandType?): Boolean {
        return com != null && com.isSameCommand(medLinkPumpMessage.commandType) ||
            com!!.isSameCommand(medLinkPumpMessage.argument)
    }

    fun nextCommand(): MedLinkCommandType {
        return if (commandPosition == 0) {
            medLinkPumpMessage.commandType
        } else if (commandPosition == 1) {
            medLinkPumpMessage.argument
        } else MedLinkCommandType.NoCommand
    }

    fun nextFunction(): Function<Supplier<Stream<String>>, out MedLinkStandardReturn<*>?>? {
        return if (functionPosition == 0 && medLinkPumpMessage.baseCallback != null) {
            currentCommand = medLinkPumpMessage.commandType
            medLinkPumpMessage.baseCallback.andThen { f: MedLinkStandardReturn<*>? ->
                functionPosition += 1
                f
            }
        } else if (functionPosition == 1 && medLinkPumpMessage.argCallback != null) {
            currentCommand = medLinkPumpMessage.argument
            medLinkPumpMessage.argCallback.andThen { f: MedLinkStandardReturn<*>? ->
                functionPosition += 1
                f
            }
        } else null
    }

    fun getCurrentCommand(): MedLinkCommandType {
        return if (commandPosition <= 1) {
            medLinkPumpMessage.commandType
        } else if (commandPosition == 2) {
            medLinkPumpMessage.argument
        } else {
            MedLinkCommandType.NoCommand
        }
    }

    open fun hasFinished(): Boolean {
        aapsLogger.info(LTag.PUMPBTCOMM, medLinkPumpMessage.toString())
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandPosition)
        return (commandPosition > 1
            || MedLinkCommandType.NoCommand.isSameCommand(
            medLinkPumpMessage.argument
        ) &&
            commandPosition > 0)
    }

    fun commandExecuted() {
        if (commandPosition == 0) {
            nrRetries++
        } else if (medLinkPumpMessage.argument != MedLinkCommandType.NoCommand) {
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
            "command=" + medLinkPumpMessage +
            ", commandPosition=" + commandPosition +
            ", functionPosition=" + functionPosition +
            ", nrRetries=" + nrRetries +
            '}'
    }

    fun matches(ext: MedLinkPumpMessage<*>): Boolean {
        return (medLinkPumpMessage.commandType.isSameCommand(ext.commandType)
            && medLinkPumpMessage.argument.isSameCommand(ext.argument))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as CommandExecutor
        return !hasFinished() && !that.hasFinished() && medLinkPumpMessage.commandType == that.medLinkPumpMessage.commandType && medLinkPumpMessage.argument == that.medLinkPumpMessage.argument
    }

    override fun hashCode(): Int {
        return Objects.hash(medLinkPumpMessage, aapsLogger, commandPosition, functionPosition, currentCommand, nrRetries)
    }

    protected fun nextCommandData(): ByteArray {
        return if (commandPosition == 0) {
            medLinkPumpMessage.commandType.raw
        } else if (commandPosition == 1) {
            medLinkPumpMessage.argumentData
        } else MedLinkCommandType.NoCommand.raw
    }

    val isInitialized: Boolean
        get() = commandPosition > 0 || functionPosition > 0

}