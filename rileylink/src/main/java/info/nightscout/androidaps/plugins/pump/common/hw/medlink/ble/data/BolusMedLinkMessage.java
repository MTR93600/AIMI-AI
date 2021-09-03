package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage<String> {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;

//    public BolusMedLinkMessage(double bolusAmount) {
//        super( MedLinkCommandType.Bolus);
//        bolusArgument.insulinAmount = bolusAmount;
//        super.argument = bolusArgument;
//        super.baseCallBack = new BolusCallback();
//    }

    public BolusMedLinkMessage(double bolusAmount,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<String>> bolusCallback) {
        super( MedLinkCommandType.Bolus);
        bolusArgument.insulinAmount = bolusAmount;
        super.argument = bolusArgument;
        super.baseCallback = bolusCallback;
    }
}
