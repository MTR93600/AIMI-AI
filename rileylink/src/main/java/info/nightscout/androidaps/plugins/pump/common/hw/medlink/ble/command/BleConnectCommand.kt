package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

class BleConnectCommand(
    aapsLogger: AAPSLogger,
    medLinkServiceData: MedLinkServiceData,
    medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract?
) :
    BleCommandReader(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    var noResponse = 0
    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCommand: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand)
        if ((lastCommand + answer).contains("pump no response")) {
            noResponse++
            if (noResponse > 2) {
                bleComm.pumpConnectionError()
                noResponse = 0
            }
        }
        if (answer.trim { it <= ' ' }.contains("ok+conn")) {
            if (answer.trim { it <= ' ' }.contains("ok+conn or command")) {
                SystemClock.sleep(500)
                bleComm.completedCommand()
                return
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            return
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}