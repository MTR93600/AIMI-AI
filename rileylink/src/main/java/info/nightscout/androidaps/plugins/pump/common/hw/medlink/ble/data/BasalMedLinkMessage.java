package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 05/02/21.
 */
public class BasalMedLinkMessage<B> extends MedLinkPumpMessage<Profile> {


    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> profileCallback;

    public BasalMedLinkMessage(MedLinkCommandType commandType, MedLinkCommandType argument,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<B>> baseCallBack,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<Profile>> profileCallback,
                               long btSleepSize,
                               BleCommand bleCommand
                               ) {
        super(commandType, argument, baseCallBack, profileCallback, btSleepSize, bleCommand);
        this.profileCallback = profileCallback;
    }

//    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> getArgCallback() {
//        return argCallBack;
//    }
}
