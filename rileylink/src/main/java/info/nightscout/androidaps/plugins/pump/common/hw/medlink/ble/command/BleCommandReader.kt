package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag

open class BleCommandReader(
    aapsLogger: AAPSLogger, medLinkServiceData: MedLinkServiceData,
    private val medLinkPumpPluginAbstract: MedLinkPumpPluginAbstract?
) : BleCommand(aapsLogger, medLinkServiceData) {

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        if (!lastCharacteristic!!.contains("ready") && !lastCharacteristic.contains("eomeomeom") &&
            (lastCharacteristic + answer).contains("ready") ||
            !lastCharacteristic.contains("eomeomeom") && answer!!.contains("eomeomeom")
        ) {
            answer?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
            rememberLastGoodDeviceCommunicationTime()
            medLinkPumpPluginAbstract?.serviceClass
            medLinkPumpPluginAbstract?.setPumpDeviceState(PumpDeviceState.Active)
            if (medLinkPumpPluginAbstract != null && !medLinkPumpPluginAbstract.isInitialized()) {
                medLinkPumpPluginAbstract.postInit()
            }
        }

        if ((lastCharacteristic + answer).contains("medtronic")) {
            if ((lastCharacteristic + answer).contains("veo")) {
                //TODO need to get better model information
                medLinkPumpPluginAbstract?.setMedtronicPumpModel("754")
            } else {
                medLinkPumpPluginAbstract?.setMedtronicPumpModel("722")
            }
        }
        super.characteristicChanged(answer, bleComm, lastCharacteristic)
    }

    private fun rememberLastGoodDeviceCommunicationTime() {
        val lastGoodReceiverCommunicationTime = System.currentTimeMillis()
        medLinkPumpPluginAbstract?.sp?.putLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime)
        medLinkPumpPluginAbstract?.pumpStatusData?.setLastCommunicationToNow()
    }
}
