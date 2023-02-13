package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

class BleConnectCommand(
    aapsLogger: AAPSLogger,
    medLinkServiceData: MedLinkServiceData,
    medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract?,
) :
    BleCommandReader(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        if ("$lastCharacteristic$answer".contains("pump no response")) {
            bleComm.pumpConnectionError()
        } else if ("$lastCharacteristic$answer".contains("ready")) {
            bleComm.clearNoResponse()
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
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }
}