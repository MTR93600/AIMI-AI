package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

class BleStartCommand(aapsLogger: AAPSLogger,
                      medLinkServiceData: MedLinkServiceData,
                      medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
) :
    BleStartStopCommand(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract) {

    // private var checkingStatus: Boolean = false

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        // if (answer!!.contains("check pump status")) {
        //     checkingStatus = true
        // } else
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        when {
            answer.contains("pump is bolusing st")  ||
            answer.contains("pump normal state")  -> {
                aapsLogger.info(LTag.PUMPBTCOMM, "status command")
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
                pumpResponse.append(answer)
                if(bleComm.currentCommand?.nextCommand()==MedLinkCommandType.NoCommand) {
                    applyResponse(pumpResponse.toString(), bleComm.currentCommand, bleComm)
                }
                pumpResponse = StringBuffer()
                bleComm.completedCommand(true)
            }

            answer.contains("pump suspend state") -> {
                bleComm.completedCommand()
            }

            else                                  -> {
                super.characteristicChanged(answer, bleComm, lastCharacteristic)
            }
        }
    }
}