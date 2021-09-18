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
class BleBolusCommand : BleCommand {

    constructor(aapsLogger: AAPSLogger?,
                medlinkServiceData: MedLinkServiceData?, runnable: Runnable?) : super(aapsLogger, medlinkServiceData, runnable) {
    }

    constructor(aapsLogger: AAPSLogger?, medlinkServiceData: MedLinkServiceData?) : super(aapsLogger, medlinkServiceData) {}

    override fun characteristicChanged(answer: String, bleComm: MedLinkBLE,
                                       lastCommand: String) {
        super.characteristicChanged(answer, bleComm, lastCommand)
        if (answer.trim { it <= ' ' }.contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm.completedCommand()
        } else if (answer.trim { it <= ' ' }.contains("is bolusing") ) {
            val currentCommand = bleComm.currentCommand
            if (currentCommand != null && currentCommand.medLinkPumpMessage != null) {
                val pumpMessage = currentCommand.medLinkPumpMessage
                if (pumpMessage is BolusMedLinkMessage && pumpMessage.bolusProgressCallback.resend) {
                    SystemClock.sleep(5000);
                    bleComm.addWriteCharacteristic(UUID.fromString(GattAttributes.SERVICE_UUID),
                        UUID.fromString(GattAttributes.GATT_UUID),
                        MedLinkPumpMessage(MedLinkCommandType.BolusStatus,
                            pumpMessage.bolusProgressCallback,
                            currentCommand.medLinkPumpMessage.btSleepTime))
                }
            }
            //            bleComm.addWriteCharacteristic();

//            new MedLinkPumpMessage<String>(MedLinkCommandType.BolusStatus,);
//            bleComm.addWriteCharacteristic(bleComm.getCurrentCommand().)
        }
    }
}