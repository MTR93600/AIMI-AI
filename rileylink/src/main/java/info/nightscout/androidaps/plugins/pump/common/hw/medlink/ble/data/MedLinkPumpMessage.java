package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkPumpMessage<B> //implements RLMessage
{
    private final MedLinkCommandType commandType;

    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> argCallback;

    protected MedLinkCommandType argument;
    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallback;
    private long btSleepTime = 0l;

    public MedLinkPumpMessage(MedLinkCommandType commandType) {
        this.commandType = commandType;
        this.argument = MedLinkCommandType.NoCommand;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Long btSleepTime) {
        this.argument = MedLinkCommandType.NoCommand;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.btSleepTime = btSleepTime;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                              MedLinkStandardReturn<B>> baseCallback,
                              Long btSleepTime) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.btSleepTime = btSleepTime;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> argCallback,
                              long btSleepTime) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.argCallback = argCallback;
        this.btSleepTime = btSleepTime;
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

    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> getBaseCallback() {
        return baseCallback;
    }

    public void setBaseCallback(Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallback) {
        this.baseCallback = baseCallback;
    }

    public Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> getArgCallback() {
        return argCallback;
    }

    public long getBtSleepTime() {
        return btSleepTime;
    }

    public void setBtSleepTime(long btSleepTime) {
        this.btSleepTime = btSleepTime;
    }

    @Override public String toString() {
        return "MedLinkPumpMessage{" +
                "commandType=" + commandType +
                ", argCallback=" + argCallback +
                ", argument=" + argument +
                ", baseCallback=" + baseCallback +
                ", btSleepTime=" + btSleepTime +
                '}';
    }
}
