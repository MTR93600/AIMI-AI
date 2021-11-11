package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

open class BlePartialCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        if (answer!!.contains("time to powerdown") && !lastCommand!!.contains("ready")) {
            super.applyResponse(pumpResponse.toString(), bleComm?.currentCommand, bleComm)
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }

}