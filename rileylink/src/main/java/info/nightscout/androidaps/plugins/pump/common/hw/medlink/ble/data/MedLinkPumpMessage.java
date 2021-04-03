package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkPumpMessage<B> //implements RLMessage
{
    private final MedLinkCommandType commandType;

    protected MedLinkCommandType argument;
    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallBack;
    private final AAPSLogger aapsLogger;
    private final MedLinkServiceData medLinkServiceData;
    protected StringBuffer pumpResponse = new StringBuffer();

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
                              MedLinkStandardReturn<B>> baseCallBack,
                              MedLinkServiceData medLinkServiceData,
                              AAPSLogger aapsLogger) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallBack = baseCallBack;
        this.medLinkServiceData = medLinkServiceData;
        this.aapsLogger = aapsLogger;
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
