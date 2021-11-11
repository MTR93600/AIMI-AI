package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

class BleStopCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleStartStopCommand(aapsLogger, medlinkServiceData) {

    private var checkingStatus: Boolean = false

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        val answers = pumpResponse.toString()
        // if (answers.contains("check pump status")) {
        //     checkingStatus = true
        // } else
        if (
        // checkingStatus && (
            answers.contains("pump suspend state")) {
            aapsLogger.info(LTag.PUMPBTCOMM, "status command")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            pumpResponse = StringBuffer()
            bleComm!!.completedCommand()
        } else if (answers.contains("pump normal state")) {
            bleComm!!.completedCommand()
        } else if (answers.contains("pump bolusing state")) {
            SystemClock.sleep(4000)
            bleComm!!.retryCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}