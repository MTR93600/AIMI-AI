package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 05/02/21.
 */
public class BasalMedLinkMessage<B> extends MedLinkPumpMessage<B> {


    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> argCallBack;

    public BasalMedLinkMessage(MedLinkCommandType commandType, MedLinkCommandType argument,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<B>> baseCallBack,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<Profile>> profileCallback,
                               MedLinkServiceData medLinkServiceData,
                               AAPSLogger aapsLogger
                               ) {
        super(commandType, argument, baseCallBack,medLinkServiceData, aapsLogger);
        this.argCallBack = profileCallback;
    }

    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> getArgCallBack() {
        return argCallBack;
    }
}
