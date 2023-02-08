package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

/**
 * Created by Dirceu on 24/03/21.
 */
class BleBolusCommand(aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData, medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract) :
    BleActivePumpCommand(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    override fun characteristicChanged(
        answer: String, bleComm: MedLinkBLE,
        lastCharacteristic: String,
    ) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        if (answer.trim { it <= ' ' }.contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm.completedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }
}