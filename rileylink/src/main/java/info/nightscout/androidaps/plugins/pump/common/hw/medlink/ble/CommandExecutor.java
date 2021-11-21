package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 01/04/21.
 */
public abstract class CommandExecutor implements Runnable {
    private final MedLinkPumpMessage<?> medLinkPumpMessage;
    private final AAPSLogger aapsLogger;
    private int commandPosition = 0;
    private int functionPosition = 0;
    private MedLinkCommandType currentCommand;

    protected int nrRetries = 0;

    protected CommandExecutor(MedLinkPumpMessage<?> medLinkPumpMessage, AAPSLogger aapsLogger) {
        this.medLinkPumpMessage = medLinkPumpMessage;
        this.aapsLogger = aapsLogger;
    }

    protected CommandExecutor(MedLinkCommandType commandType, AAPSLogger aapsLogger, MedLinkServiceData medLinkServiceData) {
        this(new MedLinkPumpMessage(commandType,
                new BleCommand(aapsLogger, medLinkServiceData)), aapsLogger);
    }

//
//    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
//        this.remainingBleCommand = remainingBleCommand;
//    }


    public boolean contains(MedLinkCommandType com) {
        return (com != null && com.isSameCommand(medLinkPumpMessage.getCommandType()) ||
                com.isSameCommand(medLinkPumpMessage.getArgument()));
    }

    public MedLinkCommandType nextCommand() {
        if (commandPosition == 0) {
            return medLinkPumpMessage.getCommandType();
        } else if (commandPosition == 1) {
            return medLinkPumpMessage.getArgument();
        } else return MedLinkCommandType.NoCommand;
    }

    public Function<Supplier<Stream<String>>, ? extends MedLinkStandardReturn<?>> nextFunction() {
        if (functionPosition == 0 && medLinkPumpMessage.getBaseCallback() != null) {
            currentCommand = medLinkPumpMessage.getCommandType();
            return medLinkPumpMessage.getBaseCallback().andThen(f -> {
                functionPosition += 1;
                return f;
            });
        } else if (functionPosition == 1 && medLinkPumpMessage.getArgCallback() != null) {
            currentCommand = medLinkPumpMessage.getArgument();
            return medLinkPumpMessage.getArgCallback().andThen(f -> {
                functionPosition += 1;
                return f;
            });
        } else return null;
    }

    public MedLinkCommandType getCurrentCommand() {
        if (commandPosition <= 1) {
            return medLinkPumpMessage.getCommandType();
        } else if (commandPosition == 2) {
            return medLinkPumpMessage.getArgument();
        } else {
            return MedLinkCommandType.NoCommand;
        }
    }

    public boolean hasFinished() {
        aapsLogger.info(LTag.PUMPBTCOMM, medLinkPumpMessage.toString());
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandPosition);
        return commandPosition > 1
                || (MedLinkCommandType.NoCommand.isSameCommand(
                medLinkPumpMessage.getArgument()) &&
                commandPosition > 0);

    }

    public void commandExecuted() {
        this.commandPosition += 1;
    }

    public void clearExecutedCommand() {
        this.commandPosition = 0;
        this.functionPosition = 0;
    }

    @NonNull @Override public String toString() {
        return "CommandExecutor{" +
                "command=" + medLinkPumpMessage +
                ", commandPosition=" + commandPosition +
                ", functionPosition=" + functionPosition +
                '}';
    }

    public MedLinkPumpMessage<?> getMedLinkPumpMessage() {
        return medLinkPumpMessage;
    }

    public int getNrRetries() {
        return nrRetries;
    }

    public boolean matches(MedLinkPumpMessage<?> ext) {
        return this.getMedLinkPumpMessage().getCommandType().isSameCommand(ext.getCommandType())
                && this.getMedLinkPumpMessage().getArgument().isSameCommand(ext.getArgument());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandExecutor that = (CommandExecutor) o;
        return !hasFinished() && !that.hasFinished() &&
                medLinkPumpMessage.getCommandType() == that.medLinkPumpMessage.getCommandType() &&
                medLinkPumpMessage.getArgument() == that.medLinkPumpMessage.getArgument();
    }

    @Override public int hashCode() {
        return Objects.hash(medLinkPumpMessage, aapsLogger, commandPosition, functionPosition, currentCommand, nrRetries);
    }

    protected byte[] nextCommandData() {
        if (commandPosition == 0) {
            return medLinkPumpMessage.getCommandType().getRaw();
        } else if (commandPosition == 1) {
            return medLinkPumpMessage.getArgumentData();
        } else return MedLinkCommandType.NoCommand.getRaw();
    }
}
