package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 24/03/21.
 */
public  class BleBolusCommand extends BleCommand {




    public BleBolusCommand(AAPSLogger aapsLogger,
                           MedLinkServiceData medlinkServiceData, Runnable runnable) {
        super(aapsLogger, medlinkServiceData, runnable);
    }

    public BleBolusCommand( AAPSLogger aapsLogger, MedLinkServiceData medlinkServiceData) {
        super(aapsLogger, medlinkServiceData);
    }

    @Override public void characteristicChanged(String answer, MedLinkBLE bleComm,
                                                String lastCommand) {
        super.characteristicChanged(answer, bleComm,lastCommand);
        if (answer.trim().contains("set bolus")) {
            aapsLogger.info(LTag.PUMPBTCOMM,pumpResponse.toString());
            bleComm.completedCommand();
        }

    }
}
