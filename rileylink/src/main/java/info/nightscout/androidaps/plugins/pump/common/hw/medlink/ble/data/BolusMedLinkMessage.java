package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage<String> {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;

    public BolusMedLinkMessage(double bolusAmount) {
        super( MedLinkCommandType.Bolus);
        bolusArgument.insulinAmount = bolusAmount;
        super.argument = bolusArgument;
        super.baseCallBack = new BolusCallback();

    }
}
