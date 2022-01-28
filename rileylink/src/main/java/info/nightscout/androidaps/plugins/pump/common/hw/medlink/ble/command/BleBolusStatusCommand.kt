package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPartialBolus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

class BleBolusStatusCommand(aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData) :
    BleCommand(aapsLogger, medLinkServiceData) {

    private var status: MedLinkPartialBolus = MedLinkPartialBolus(PumpType.MedLink_Medtronic_554_754_Veo)

    override fun characteristicChanged(answer: String?, bleComm: MedLinkBLE?, lastCommand: String?) {
        answer?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
        if (answer?.contains("time to powerdown 5") == true) {
            bleComm?.nextCommand();
        } else if (answer?.contains("ready") == true ) {
            val fullResponse = pumpResponse.toString()
            val responseIterator = fullResponse.substring(fullResponse.indexOf("last")).split("\n").iterator()
            status = MedLinkStatusParser.parseBolusInfo(
                responseIterator, status);
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            aapsLogger.info(LTag.PUMPBTCOMM, status.toString())
            if (status.lastBolusAmount != null && status.bolusDeliveredAmount  > 0  &&
                status.bolusDeliveredAmount < status.lastBolusAmount!!
            ) {
                bleComm?.clearExecutedCommand()
            } else {
                applyResponse(pumpResponse.toString(), bleComm?.currentCommand, bleComm)
                bleComm?.completedCommand()
            }
            pumpResponse = StringBuffer()
        } else if (answer?.contains("time to powerdown") == false) {
            super.characteristicChanged(answer, bleComm, lastCommand)
        }
    }
}