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
        if (answer.trim { it <= ' ' }.contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            bleComm.completedCommand()
            bleComm.nextCommand()
            return
        }
        // else if (answer.trim { it <= ' ' }.contains("is bolusing") || (
        //         bleComm.currentCommand != null &&
        //             MedLinkCommandType.BolusStatus.isSameCommand(bleComm.currentCommand.currentCommand))) {
        //     val currentCommand = bleComm.currentCommand
        //     if (currentCommand != null && currentCommand.medLinkPumpMessage != null) {
        //         val pumpMessage = currentCommand.medLinkPumpMessage
        //         aapsLogger.info(LTag.PUMPBTCOMM,currentCommand.toString())
        //         aapsLogger.info(LTag.PUMPBTCOMM,pumpMessage.toString())
        //         SystemClock.sleep(5000)
        //         if (pumpMessage is BolusMedLinkMessage && pumpMessage.bolusProgressCallback.resend) {
        //
        //             bleComm.addWriteCharacteristic(UUID.fromString(GattAttributes.SERVICE_UUID),
        //                 UUID.fromString(GattAttributes.GATT_UUID),
        //                 MedLinkPumpMessage(MedLinkCommandType.BolusStatus,
        //                     pumpMessage.bolusProgressCallback.copy(commandExecutor = currentCommand),
        //                     currentCommand.medLinkPumpMessage.btSleepTime),true)
        //             return;
        //         }
        //     }
            //            bleComm.addWriteCharacteristic();

//            new MedLinkPumpMessage<String>(MedLinkCommandType.BolusStatus,);
//            bleComm.addWriteCharacteristic(bleComm.getCurrentCommand().)
//         }
        super.characteristicChanged(answer, bleComm, lastCommand)

    }
}