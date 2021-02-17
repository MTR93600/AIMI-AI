package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkPumpMessage<B> //implements RLMessage
{
    private final MedLinkCommandType commandType;

    protected MedLinkCommandType argument;
    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallBack;


    public MedLinkPumpMessage(MedLinkCommandType commandType) {
        this.commandType = commandType;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType, MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallBack) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallBack = baseCallBack;
    }

    public MedLinkCommandType getCommandType() {
        return commandType;
    }

    public MedLinkCommandType getArgument() {
        return argument;
    }

    public byte[] getCommandData() {
        return commandType.getRaw();
    }


    public byte[] getArgumentData() {
        if (argument != null) {
            return argument.getRaw();
        } else {
            return new byte[0];
        }

    }


    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> getBaseCallBack() {
        return baseCallBack;
    }

    public void setBaseCallBack(Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallBack) {
        this.baseCallBack = baseCallBack;
    }

}
