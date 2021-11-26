package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

open class BlePartialCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer.contains("time to powerdown") && !pumpResponse.toString().contains("ready") &&
            !pumpResponse.toString().contains("---beginning of data") || answer.contains("eginning of data")) {
            super.applyResponse(pumpResponse.toString(), bleComm?.currentCommand, bleComm)
            pumpResponse = StringBuffer()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }

}