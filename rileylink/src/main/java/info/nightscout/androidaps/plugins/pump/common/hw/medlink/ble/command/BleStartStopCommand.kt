package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

open class BleStartStopCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) : BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        if (answer?.contains("set pump state tim") == true) {
            bleComm?.currentCommand?.clearExecutedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}