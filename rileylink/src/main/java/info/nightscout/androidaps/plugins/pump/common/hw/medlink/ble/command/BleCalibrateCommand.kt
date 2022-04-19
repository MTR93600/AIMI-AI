package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

class BleCalibrateCommand(aapsLogger: AAPSLogger?, medLinkServiceData: MedLinkServiceData?) : BleSuspendedCommand(aapsLogger, medLinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer.trim { it <= ' ' }.contains("enter calibration val")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm?.completedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }

    }

}