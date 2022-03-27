package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage<String> {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;
    private final MedLinkPumpMessage bolusProgressMessage;
    private final DetailedBolusInfo detailedBolusInfo;

    private final List<MedLinkPumpMessage> startStopCommands;
    private final boolean shouldBeSuspended;

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
                               MedLinkPumpMessage bolusProgressMessage, BleBolusCommand bleBolusCommand,
                               List<MedLinkPumpMessage> postCommands,
                               boolean shouldBeSuspended) {
        super(bolusCommand, bleBolusCommand);
        this.detailedBolusInfo = detailedBolusInfo;
        super.argument = bolusArgument;
        super.baseCallback = bolusCallback;
        this.bolusProgressMessage = bolusProgressMessage;
        this.startStopCommands = postCommands;
        this.shouldBeSuspended = shouldBeSuspended;
    }

    public DetailedBolusInfo getDetailedBolusInfo() {
        return detailedBolusInfo;
    }

    @Override public byte[] getArgumentData() {
        bolusArgument.insulinAmount = detailedBolusInfo.insulin;
        return bolusArgument.getRaw();
    }

    public MedLinkPumpMessage getBolusProgressMessage() {
        return bolusProgressMessage;
    }

    public List<MedLinkPumpMessage> getStartStopCommands() {
        return startStopCommands;
    }

    public boolean isShouldBeSuspended() {
        return shouldBeSuspended;
    }
}
