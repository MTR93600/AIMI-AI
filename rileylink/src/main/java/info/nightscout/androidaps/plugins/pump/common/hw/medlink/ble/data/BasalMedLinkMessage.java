package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 05/02/21.
 */
public class BasalMedLinkMessage<B> extends MedLinkPumpMessage<B> {


    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> argCallBack;

    public BasalMedLinkMessage(MedLinkCommandType commandType, MedLinkCommandType argument,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<B>> baseCallBack,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<Profile>> profileCallback
                               ) {
        super(commandType, argument, baseCallBack);
        this.argCallBack = profileCallback;
    }

    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> getArgCallBack() {
        return argCallBack;
    }
}
