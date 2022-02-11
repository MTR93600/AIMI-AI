package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.shared.logging.AAPSLogger

abstract class ContinuousCommandExecutor<B>(val message: MedLinkPumpMessage<B>, aapsLogger: AAPSLogger) : CommandExecutor(message, aapsLogger) {

    var count = 0
    override fun hasFinished(): Boolean {
        val callback = message.baseCallback
        return if (callback is BolusProgressCallback && callback.resend && count < 7) {
            clearExecutedCommand()
            count ++
            false;
        } else {
            count = 0
            super.hasFinished()
        }
    }

    override fun clearExecutedCommand() {
        super.clearExecutedCommand()
        nrRetries = 0
    }
}