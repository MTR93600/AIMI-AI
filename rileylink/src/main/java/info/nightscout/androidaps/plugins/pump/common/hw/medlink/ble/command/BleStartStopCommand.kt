package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

open class BleStartStopCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) : BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer?.contains("set pump state tim")) {
            bleComm?.currentCommand?.clearExecutedCommand()
            bleComm?.retryCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}