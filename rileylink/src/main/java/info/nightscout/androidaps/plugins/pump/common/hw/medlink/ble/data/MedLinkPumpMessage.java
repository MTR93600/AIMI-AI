package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleStopCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkPumpMessage<B> //implements RLMessage
{

    private BleCommand bleCommand;
    private boolean priority = false;
    private final MedLinkCommandType commandType;

    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> argCallback;

    @NotNull protected MedLinkCommandType argument;
    protected Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseCallback;
    private long btSleepTime = 0L;

    public MedLinkPumpMessage(MedLinkCommandType commandType, BleCommand bleCommand) {
        this.commandType = commandType;
        this.argument = MedLinkCommandType.NoCommand;
        this.bleCommand = bleCommand;
    }


    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Long btSleepTime, BleCommand bleCommand) {
        this.argument = MedLinkCommandType.NoCommand;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.btSleepTime = btSleepTime;
        this.bleCommand = bleCommand;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              @NonNull MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Long btSleepTime,
                              boolean priority, BleCommand bleCommand) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.btSleepTime = btSleepTime;
        this.priority = priority;
        this.bleCommand = bleCommand;
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Long btSleepTime, BleCommand bleCommand) {
        this(commandType, argument, baseCallback, btSleepTime, false, bleCommand);
    }

    public MedLinkPumpMessage(MedLinkCommandType commandType,
                              @NonNull MedLinkCommandType argument,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> baseCallback,
                              Function<Supplier<Stream<String>>,
                                      MedLinkStandardReturn<B>> argCallback,
                              long btSleepTime, BleCommand bleCommand) {
        this.argument = argument;
        this.commandType = commandType;
        this.baseCallback = baseCallback;
        this.argCallback = argCallback;
        this.btSleepTime = btSleepTime;
        this.bleCommand = bleCommand;
    }

    public MedLinkPumpMessage(@NotNull MedLinkCommandType command,
                              @NotNull MedLinkCommandType argument,
                              @NotNull BleCommand bleCommand) {
        this.commandType = command;
        this.argument = argument;
        this.bleCommand = bleCommand;

    }


    public MedLinkCommandType getCommandType() {
        return commandType;
    }

    @NonNull public MedLinkCommandType getArgument() {
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

    @NonNull @Override public String toString() {
        return "MedLinkPumpMessage{" +
                "commandType=" + commandType +
                ", argCallback=" + argCallback +
                ", argument=" + argument +
                ", baseCallback=" + baseCallback +
                ", btSleepTime=" + btSleepTime +
                '}';
    }

    public boolean isPriority() {
        return priority;
    }

    public void characteristicChanged(String answer, MedLinkBLE bleComm, String lastCommand) {
        bleCommand.characteristicChanged(answer, bleComm, lastCommand);
    }
}
