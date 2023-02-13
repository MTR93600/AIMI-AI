package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

class BolusStatusMedLinkMessage<B>(
                                   baseCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
                                   btSleepTime: Long, bleCommand: BleCommand) : MedLinkPumpMessage<B, Any>(
    MedLinkCommandType.BolusStatus, MedLinkCommandType.NoCommand, baseCallback, btSleepTime, bleCommand, CommandPriority.LOWER) {

}