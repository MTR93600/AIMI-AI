package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.SystemClock
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusMedLinkMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import java.util.*

/**
 * Created by Dirceu on 24/03/21.
 */
class BleBolusCommand : BleSuspendedCommand {
    constructor(aapsLogger: AAPSLogger?,
                medlinkServiceData: MedLinkServiceData?) : super(aapsLogger, medlinkServiceData) {
    }

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?,
                                       lastCommand: String?) {
        if (answer!!.trim { it <= ' ' }.contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm?.completedCommand()
            // bleComm?.nextCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}