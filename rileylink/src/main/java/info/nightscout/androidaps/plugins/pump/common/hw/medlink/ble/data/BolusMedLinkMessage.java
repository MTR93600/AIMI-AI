package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage<String> {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;
    private final MedLinkPumpMessage bolusProgressMessage;
    private final double bolusAmount;

    private final List<MedLinkPumpMessage> startStopCommands;
    private final boolean shouldbesuspended;

//    public BolusMedLinkMessage(double bolusAmount) {
//        super( MedLinkCommandType.Bolus);
//        bolusArgument.insulinAmount = bolusAmount;
//        super.argument = bolusArgument;
//        super.baseCallBack = new BolusCallback();
//    }

    public BolusMedLinkMessage(MedLinkCommandType bolusCommand,
                               double bolusAmount,
                               Function<Supplier<Stream<String>>,
                                       MedLinkStandardReturn<String>> bolusCallback,
                               MedLinkPumpMessage bolusProgressMessage, BleBolusCommand bleBolusCommand,
                               List<MedLinkPumpMessage> postCommands,
                               boolean shouldBeSuspended) {
        super(bolusCommand, bleBolusCommand);
        this.bolusAmount = bolusAmount;
        super.argument = bolusArgument;
        super.baseCallback = bolusCallback;
        this.bolusProgressMessage = bolusProgressMessage;
        this.startStopCommands = postCommands;
        this.shouldbesuspended = shouldBeSuspended;
    }

    public double getBolusAmount() {
        return bolusAmount;
    }

    @Override public byte[] getArgumentData() {
        bolusArgument.insulinAmount = bolusAmount;
        return bolusArgument.getRaw();
    }

    public MedLinkPumpMessage getBolusProgressMessage() {
        return bolusProgressMessage;
    }

    public List<MedLinkPumpMessage> getStartStopCommands() {
        return startStopCommands;
    }

    public boolean isShouldbesuspended() {
        return shouldbesuspended;
    }
}
