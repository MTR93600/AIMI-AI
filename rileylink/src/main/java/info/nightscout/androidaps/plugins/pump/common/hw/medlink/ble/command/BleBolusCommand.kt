package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

/**
 * Created by Dirceu on 24/03/21.
 */
class BleBolusCommand : BleActivePumpCommand {
    constructor(aapsLogger: AAPSLogger,
                medLinkServiceData: MedLinkServiceData,
                medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract
    ) : super(aapsLogger, medLinkServiceData, medLinkPumpPluginAbstract)

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE,
                                       lastCommand: String) {
        aapsLogger.info(LTag.PUMPBTCOMM, answer)
        aapsLogger.info(LTag.PUMPBTCOMM, lastCommand)
        if (answer.trim { it <= ' ' }.contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm.completedCommand()
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}