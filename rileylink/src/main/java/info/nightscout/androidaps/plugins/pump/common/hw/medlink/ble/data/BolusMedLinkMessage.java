package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusResultActivity;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;

    public BolusMedLinkMessage(AAPSLogger aapsLogger, double bolusAmount) {
        super(aapsLogger, MedLinkCommandType.Bolus);
        bolusArgument.insulinAmount = bolusAmount;
        super.baseResultActivity = new BolusResultActivity(aapsLogger);

    }
}
