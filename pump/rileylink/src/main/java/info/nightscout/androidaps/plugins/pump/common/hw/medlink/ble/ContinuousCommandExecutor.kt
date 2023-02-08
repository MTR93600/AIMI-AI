package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.CommandStructure
import info.nightscout.rx.logging.AAPSLogger

abstract class ContinuousCommandExecutor<B>(
    val commands: MutableList<
        CommandStructure<B, BleCommand>>, aapsLogger: AAPSLogger
) : CommandExecutor<B>(
    commandList = commands,
    aapsLogger = aapsLogger
) {

    var count = 0
    var bolusProgress = 0.0
    override fun hasFinished(): Boolean {
        val callback = commands[0].parseFunction.get()
        return super.hasFinished()
        // if (callback is BolusProgressCallback){
        //
        //     aapsLogger.info(LTag.PUMPBTCOMM, "${bolusProgress}")
        //     aapsLogger.info(LTag.PUMPBTCOMM, "${callback.pumpStatus.bolusDeliveredAmount}")
        //     if(callback.resend && bolusProgress < callback.pumpStatus.bolusDeliveredAmount) {
        //         clearExecutedCommand()
        //         bolusProgress = callback.pumpStatus.bolusDeliveredAmount
        //         count++
        //         false
        //     } else {
        //         true
        //     }
        // } else {
        //     count = 0
        //     super.hasFinished()
        // }

    }

    override fun clearExecutedCommand() {
        super.clearExecutedCommand()
        nrRetries = 0
    }
}