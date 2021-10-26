package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import androidx.annotation.NonNull;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

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

    protected CommandExecutor(MedLinkCommandType medLinkPumpMessage, AAPSLogger aapsLogger) {
        this(new MedLinkPumpMessage(medLinkPumpMessage), aapsLogger);
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
        aapsLogger.info(LTag.PUMPBTCOMM,medLinkPumpMessage.toString());
        aapsLogger.info(LTag.PUMPBTCOMM,""+commandPosition);
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
}
