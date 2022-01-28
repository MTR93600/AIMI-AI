package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.shared.logging.AAPSLogger

abstract class ContinuousCommandExecutor<B>(val message: MedLinkPumpMessage<B>, aapsLogger: AAPSLogger) : CommandExecutor(message, aapsLogger) {

    override fun hasFinished(): Boolean {
        val callback = message.baseCallback
        return if (callback is BolusProgressCallback && callback.resend) {
            clearExecutedCommand()
            false;
        } else {
            super.hasFinished()
        }
    }

    override fun clearExecutedCommand() {
        super.clearExecutedCommand()
        nrRetries = 0
    }
}