package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.*

open class BleActivePumpCommand(
    aapsLogger: AAPSLogger,
    medLinkServiceData: MedLinkServiceData,
    medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
) :
    BleCommandReader(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        if (answer.contains("pump suspend state")) {
            bleComm.needToBeStarted<Any, Any>(UUID.fromString(GattAttributes.SERVICE_UUID),
                                              UUID.fromString(GattAttributes.GATT_UUID), bleComm.currentCommand?.getCurrentCommand()
            )
            bleComm.clearExecutedCommand();
            bleComm.nextCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCharacteristic)
        }
    }
}