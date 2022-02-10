package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import java.util.*

open class BleSuspendedCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer.contains("pump suspend state")) {
            bleComm?.needToBeStarted(UUID.fromString(GattAttributes.SERVICE_UUID),
                                     UUID.fromString(GattAttributes.GATT_UUID), bleComm.currentCommand?.getCurrentCommand()
            )
            bleComm?.clearExecutedCommand();
            bleComm?.nextCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}