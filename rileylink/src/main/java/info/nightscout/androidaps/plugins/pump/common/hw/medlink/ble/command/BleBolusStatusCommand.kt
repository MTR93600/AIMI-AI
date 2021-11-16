package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPartialBolus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser

class BleBolusStatusCommand(aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData) :
    BleCommand(aapsLogger, medLinkServiceData) {

    private var status: MedLinkPartialBolus = MedLinkPartialBolus(PumpType.MedLink_Medtronic_554_754_Veo)

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        answer?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
        if (answer?.contains("time to powerdown 5") == true) {
            bleComm?.nextCommand();
        } else if (answer?.contains("ready") == true) {
            status = MedLinkStatusParser.parseBolusInfo(
                pumpResponse.toString().split("\n").iterator(), status);
            aapsLogger.info(LTag.PUMPBTCOMM,status.toString())
            if (status.lastBolusAmount != null && status.bolusDeliveredAmount < status.lastBolusAmount) {
                bleComm?.clearExecutedCommand()
            } else {
                applyResponse(pumpResponse.toString(), bleComm?.currentCommand, bleComm)
            }
        } else {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}