package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

class BleConnectCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer.trim { it <= ' ' }.contains("ok+conn")) {
            if (answer.trim { it <= ' ' }.contains("ok+conn or command")) {
                SystemClock.sleep(500)
                bleComm!!.completedCommand()
                return
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            return
        }else{
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}