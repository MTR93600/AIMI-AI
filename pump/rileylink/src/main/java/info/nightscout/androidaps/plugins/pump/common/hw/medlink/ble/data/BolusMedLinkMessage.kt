package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data


import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.interfaces.pump.DetailedBolusInfo
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 21/12/20.
 */
class BolusMedLinkMessage(
    command: MedLinkCommandType,
    bolusArgument: MedLinkCommandType,
    bolusCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>>,
    bolusProgressMessage: MedLinkPumpMessage<String, Any>?,
    btSleepTime: Long,
    bleBolusCommand: BleBolusCommand,
    shouldBeSuspended: Boolean,
    bolusAmount: Double
) : StartStopMessage<String,String>(command, bolusArgument, bolusCallback, null, btSleepTime, bleBolusCommand, shouldBeSuspended,
                             if(bolusAmount>=10) " $bolusAmount" else "  $bolusAmount") {

    init {
        bolusProgressMessage?.let { supplementalCommands.addAll(it.commands) }
    }

    companion object {

        operator fun invoke(
            command: MedLinkCommandType,
            detailedBolusInfo: DetailedBolusInfo,
            bolusCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>>,
            bolusProgressMessage: MedLinkPumpMessage<String, Any>?,
            btSleepTime: Long,
            bleBolusCommand: BleBolusCommand,
            shouldBeSuspended: Boolean
        ): BolusMedLinkMessage {
            bolusArgument.insulinAmount = detailedBolusInfo.insulin
            return BolusMedLinkMessage(command, bolusArgument, bolusCallback, bolusProgressMessage, btSleepTime, bleBolusCommand, shouldBeSuspended,
            detailedBolusInfo.insulin)
        }
        private val bolusArgument = MedLinkCommandType.BolusAmount
    }

    override fun toString(): String {
        return super.toString() + {
            "insulinAmount: ${bolusArgument.insulinAmount}"
        }
    }
}