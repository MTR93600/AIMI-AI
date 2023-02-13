package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

class BleCalCommand(aapsLogger: AAPSLogger, medlinkServiceData: MedLinkServiceData) : BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        when {
            answer.contains("calibration confirmed from pump")     -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "success calibrated")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                pumpResponse = StringBuffer()
                bleComm.completedCommand()
            }
            answer.contains("calibration not confirmed from pump") -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "calibration failed")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                pumpResponse = StringBuffer()
                SystemClock.sleep(60000)
                bleComm.retryCommand()
            }
            else                                                   -> {
                super.characteristicChanged(answer, bleComm, lastCharacteristic)
            }
        }
    }
}