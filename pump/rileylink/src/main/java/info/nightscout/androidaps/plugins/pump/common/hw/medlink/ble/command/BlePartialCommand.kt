package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.rx.logging.AAPSLogger

open class BlePartialCommand(aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData) :
    BleCommand(aapsLogger, medLinkServiceData) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        if (answer.contains("time to powerdown") && !pumpResponse.toString().contains("ready")) {
            val resp = pumpResponse.toString()
            pumpResponse = StringBuffer()
            super.applyResponse(resp, bleComm.currentCommand, bleComm)

        } else {
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }

}