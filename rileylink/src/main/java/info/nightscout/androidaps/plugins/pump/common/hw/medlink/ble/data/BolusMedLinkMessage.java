package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends StartStopMessage<String> {

    private static final MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;
    private final MedLinkPumpMessage<String> bolusProgressMessage;
    private final DetailedBolusInfo detailedBolusInfo;


//    public BolusMedLinkMessage(double bolusAmount) {
//        super( MedLinkCommandType.Bolus);
//        bolusArgument.insulinAmount = bolusAmount;
//        super.argument = bolusArgument;
//        super.baseCallBack = new BolusCallback();
//    }

    public BolusMedLinkMessage(MedLinkCommandType bolusCommand,
                               DetailedBolusInfo detailedBolusInfo,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<String>> bolusCallback,
                               MedLinkPumpMessage<String> bolusProgressMessage,
                               BleBolusCommand bleBolusCommand,
                               List<MedLinkPumpMessage<PumpDriverState>> postCommands,
                               boolean shouldBeSuspended) {
        super(bolusCommand, bleBolusCommand, postCommands, shouldBeSuspended);
        this.detailedBolusInfo = detailedBolusInfo;
        super.argument = bolusArgument;
        super.baseCallback = bolusCallback;
        this.bolusProgressMessage = bolusProgressMessage;

    }

    public DetailedBolusInfo getDetailedBolusInfo() {
        return detailedBolusInfo;
    }

    @Override public byte[] getArgumentData() {
        bolusArgument.insulinAmount = detailedBolusInfo.insulin;
        return bolusArgument.getRaw();
    }

    public MedLinkPumpMessage<String> getBolusProgressMessage() {
        return bolusProgressMessage;
    }

}
