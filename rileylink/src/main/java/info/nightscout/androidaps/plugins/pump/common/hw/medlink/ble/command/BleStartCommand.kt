package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

class BleStartCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleStartStopCommand(aapsLogger, medlinkServiceData) {

    private var checkingStatus: Boolean = false

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        // if (answer!!.contains("check pump status")) {
        //     checkingStatus = true
        // } else
        when {
            answer!!.contains("pump normal state") -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "status command")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                pumpResponse = StringBuffer()
                bleComm?.completedCommand()
            }
            answer.contains("pump suspend state")  -> {
                bleComm?.completedCommand()
            }
            else                                   -> {
                super.characteristicChanged(answer, bleComm, lastCommand)
            }
        }
    }
}