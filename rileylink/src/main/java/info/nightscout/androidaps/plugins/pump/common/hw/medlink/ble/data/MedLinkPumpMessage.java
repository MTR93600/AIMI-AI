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
    private final AAPSLogger aapsLogger;
    private final MedLinkServiceData medLinkServiceData;
    protected StringBuffer pumpResponse = new StringBuffer();
    private long btSleepTime = 0l;

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkServiceData medLinkServiceData,
                              AAPSLogger aapsLogger) {
        this.commandType = commandType;
        this.medLinkServiceData = medLinkServiceData;
        this.aapsLogger = aapsLogger;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                              MedLinkStandardReturn<B>> baseCallback,
                              MedLinkServiceData medLinkServiceData,
                              AAPSLogger aapsLogger, Long btSleepTime) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.medLinkServiceData = medLinkServiceData;
        this.aapsLogger = aapsLogger;
        this.btSleepTime = btSleepTime;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> argCallback,
                              MedLinkServiceData medLinkServiceData,
                              AAPSLogger aapsLogger, long btSleepTime) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.medLinkServiceData = medLinkServiceData;
        this.aapsLogger = aapsLogger;
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
}
