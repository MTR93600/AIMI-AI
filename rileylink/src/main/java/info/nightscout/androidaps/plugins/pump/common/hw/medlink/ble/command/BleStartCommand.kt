package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

class BleStartCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleStartStopCommand(aapsLogger, medlinkServiceData) {

    private var checkingStatus: Boolean = false

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        // if (answer!!.contains("check pump status")) {
        //     checkingStatus = true
        // } else
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        when {
            answer.contains("pump normal state")  -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "status command")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                applyResponse(pumpResponse.toString(), bleComm?.currentCommand, bleComm)
                pumpResponse = StringBuffer()
                bleComm?.completedCommand(true)
            }

            answer.contains("pump suspend state") -> {
                bleComm?.completedCommand()
            }

            else                                  -> {
                super.characteristicChanged(answer, bleComm, lastCommand)
            }
        }
    }
}