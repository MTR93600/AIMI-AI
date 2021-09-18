package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 01/04/21.
 */
public abstract class CommandExecutor implements Runnable {
    private final MedLinkPumpMessage<?> medLinkPumpMessage;
    private int commandPosition = 0;
    private int functionPosition = 0;
    private MedLinkCommandType currentCommand;

    protected int nrRetries = 0;

    protected CommandExecutor(MedLinkPumpMessage<?> medLinkPumpMessage) {
        this.medLinkPumpMessage = medLinkPumpMessage;
    }

    protected CommandExecutor(MedLinkCommandType medLinkPumpMessage) {
        this(new MedLinkPumpMessage(medLinkPumpMessage));
    }

//
//    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
//        this.remainingBleCommand = remainingBleCommand;
//    }


    public boolean contains(MedLinkCommandType com){
        return (com != null && com.isSameCommand(medLinkPumpMessage.getCommandType())  ||
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
        if (functionPosition == 0 && medLinkPumpMessage.getBaseCallback() !=null) {
            currentCommand = medLinkPumpMessage.getCommandType();
            return medLinkPumpMessage.getBaseCallback().andThen(f -> {
                functionPosition+=1;
                return f;
            });
        } else if (functionPosition == 1 && medLinkPumpMessage.getArgCallback() != null) {
            currentCommand = medLinkPumpMessage.getArgument();
            return medLinkPumpMessage.getArgCallback().andThen(f -> {
                functionPosition+=1;
                return f;
            });
        } else return null;
    }

    public MedLinkCommandType getCurrentCommand(){
        return currentCommand;
    }

    public boolean hasFinished() {
        return commandPosition > 1
        || (medLinkPumpMessage.getArgument().isSameCommand(MedLinkCommandType.NoCommand) &&
                        commandPosition > 0);
    }

    public void commandExecuted() {
        this.commandPosition += 1;
    }

    public void clearExecutedCommand() {
        this.commandPosition = 0;
        this.functionPosition=0;
    }

    @Override public String toString() {
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
