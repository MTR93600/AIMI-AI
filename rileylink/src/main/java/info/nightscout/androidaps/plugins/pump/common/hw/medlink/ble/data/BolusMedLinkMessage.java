package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 21/12/20.
 */
public class BolusMedLinkMessage extends MedLinkPumpMessage<String> {

    private static MedLinkCommandType bolusArgument = MedLinkCommandType.BolusAmount;
    private final BolusProgressCallback bolusProgressCallback;
    private final double bolusAmount;

    private final List<MedLinkPumpMessage> startStopCommands;

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
                               BolusProgressCallback bolusProgressCallback, BleBolusCommand bleBolusCommand,
                               List<MedLinkPumpMessage> postComands) {
        super(bolusCommand, bleBolusCommand);
        this.bolusAmount = bolusAmount;
        super.argument = bolusArgument;
        super.baseCallback = bolusCallback;
        this.bolusProgressCallback = bolusProgressCallback;
        this.startStopCommands = postComands;
    }

    public double getBolusAmount() {
        return bolusAmount;
    }

    @Override public byte[] getArgumentData() {
        bolusArgument.insulinAmount = bolusAmount;
        return bolusArgument.getRaw();
    }

    public BolusProgressCallback getBolusProgressCallback() {
        return bolusProgressCallback;
    }

    public List<MedLinkPumpMessage> getStartStopCommands() {
        return startStopCommands;
    }

}
