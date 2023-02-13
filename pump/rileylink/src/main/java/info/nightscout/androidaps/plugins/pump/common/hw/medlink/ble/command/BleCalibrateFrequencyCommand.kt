package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.rx.logging.AAPSLogger

class BleCalibrateFrequencyCommand(aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData) : BleCommand(aapsLogger, medLinkServiceData) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        if (answer?.contains("-9 to +9") == true) {
            bleComm?.completedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }
}