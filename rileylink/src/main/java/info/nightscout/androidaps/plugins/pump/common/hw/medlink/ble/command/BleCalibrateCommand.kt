package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

class BleCalibrateCommand(
    aapsLogger: AAPSLogger,
    medLinkServiceData: MedLinkServiceData,
    medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
) :
    BleActivePumpCommand(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCommand: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand)
        if ((lastCommand + answer).trim { it <= ' ' }.contains("nter calibration value")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm.completedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }

    }

}