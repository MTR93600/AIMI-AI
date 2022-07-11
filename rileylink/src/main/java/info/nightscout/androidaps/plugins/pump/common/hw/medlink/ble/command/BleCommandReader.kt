package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import java.util.*
import java.util.function.Predicate

open class BleCommandReader(
    aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData,
    private val medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract?
) : BleCommand(aapsLogger, medLinkServiceData) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCommand: String) {
        if (!lastCommand!!.contains("ready") && !lastCommand.contains("eomeomeom") &&
            (lastCommand + answer).contains("ready") ||
            !lastCommand.contains("eomeomeom") && answer!!.contains("eomeomeom")
        ) {
            answer?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
            rememberLastGoodDeviceCommunicationTime()
            medLinkPumpPluginAbstract?.serviceClass
            medLinkPumpPluginAbstract?.setPumpDeviceState(PumpDeviceState.Active)
            if (medLinkPumpPluginAbstract != null && !medLinkPumpPluginAbstract.isInitialized()) {
                medLinkPumpPluginAbstract.postInit()
            }
        }

        if ((lastCommand + answer).contains("medtronic")) {
            if ((lastCommand + answer).contains("veo")) {
                //TODO need to get better model information
                medLinkPumpPluginAbstract?.setMedtronicPumpModel("754")
            } else {
                medLinkPumpPluginAbstract?.setMedtronicPumpModel("722")
            }
        }
        super.characteristicChanged(answer, bleComm, lastCommand)
    }

    private fun rememberLastGoodDeviceCommunicationTime() {
        val lastGoodReceiverCommunicationTime = System.currentTimeMillis()
        medLinkPumpPluginAbstract?.sp?.putLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime)
        medLinkPumpPluginAbstract?.pumpStatusData?.setLastCommunicationToNow()
    }
}
