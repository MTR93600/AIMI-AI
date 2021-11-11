package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData

class BleCalCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) : BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        if (answer!!.contains("calibration confirmed from pump")) {
            aapsLogger.info(LTag.PUMPBTCOMM, "success calibrated")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            pumpResponse = StringBuffer()
            bleComm!!.completedCommand()
        } else if (answer!!.contains("calibration not confirmed from pump")) {
            aapsLogger.info(LTag.PUMPBTCOMM, "calibration failed")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            pumpResponse = StringBuffer()
            SystemClock.sleep(60000)
            bleComm!!.retryCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}