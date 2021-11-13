package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import java.util.*

open class BleSuspendedCommand(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) :
    BleCommand(aapsLogger, medlinkServiceData) {

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer!!)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand!!)
        if (answer.contains("pump suspend state")) {
            val msg = MedLinkPumpMessage<String>(MedLinkCommandType.StopStartPump,
                MedLinkCommandType.StartPump,
                BleStartCommand(aapsLogger, medLinkServiceData))
            bleComm?.currentCommand?.clearExecutedCommand();
            bleComm?.addWriteCharacteristic(UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID), msg, true)
            bleComm?.nextCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}