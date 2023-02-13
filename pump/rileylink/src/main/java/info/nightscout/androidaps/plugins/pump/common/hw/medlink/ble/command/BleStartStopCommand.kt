package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

open class BleStartStopCommand(
    aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData,
    medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
) :
    BleCommandReader(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        if (answer.contains("set pump state tim")) {
            bleComm.currentCommand?.clearExecutedCommand()
            bleComm.retryCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }
}