package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

class BleStopCommand(aapsLogger: AAPSLogger,
                     medLinkServiceData: MedLinkServiceData,
                     medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
) :
    BleStartStopCommand(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    // private var checkingStatus: Boolean = false

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        // val answers = pumpResponse.toString()
        // if (answers.contains("check pump status")) {
        //     checkingStatus = true
        // } else
        when {
            // checkingStatus && (
            answer.contains("pump suspend state")  -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "status command")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                pumpResponse.append(answer)
                super.applyResponse(pumpResponse.toString(), bleComm.currentCommand, bleComm)
                pumpResponse = StringBuffer()
                bleComm.completedCommand(true)
            }
            answer.contains("pump normal state")   -> {
                bleComm.completedCommand()
            }
            answer.contains("pump bolusing state") -> {
                SystemClock.sleep(5000)
                bleComm.currentCommand?.clearExecutedCommand()
                bleComm.retryCommand()
            }
            else                                   -> {
                super.characteristicChanged(answer, bleComm, lastCharacteristic)
            }
        }
    }
}