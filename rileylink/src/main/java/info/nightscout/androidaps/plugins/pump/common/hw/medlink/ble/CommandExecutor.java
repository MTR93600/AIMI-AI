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
    private final MedLinkPumpMessage<?> command;
    private int commandPosition = 0;
    private int functionPosition = 0;
    private MedLinkCommandType currentCommand;

    protected int nrRetries = 0;

    protected CommandExecutor(MedLinkPumpMessage<?> command) {
        this.command = command;
    }

    protected CommandExecutor(MedLinkCommandType command) {
        this(new MedLinkPumpMessage(command));
    }

//
//    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
//        this.remainingBleCommand = remainingBleCommand;
//    }


    public boolean contains(MedLinkCommandType com){
        return (com != null && com.isSameCommand(command.getCommandType())  ||
                 com.isSameCommand(command.getArgument()));
    }

    public MedLinkCommandType nextCommand() {
        if (commandPosition == 0) {
            return command.getCommandType();
        } else if (commandPosition == 1) {
            return command.getArgument();
        } else return MedLinkCommandType.NoCommand;
    }

    public Function<Supplier<Stream<String>>, ? extends MedLinkStandardReturn<?>> nextFunction() {
        if (functionPosition == 0 && command.getBaseCallback() !=null) {
            currentCommand = command.getCommandType();
            return command.getBaseCallback().andThen(f -> {
                functionPosition+=1;
                return f;
            });
        } else if (functionPosition == 1 && command.getArgCallback() != null) {
            currentCommand = command.getArgument();
            return command.getArgCallback().andThen(f -> {
                functionPosition+=1;
                return f;
            });
        } else return null;
    }

    public MedLinkCommandType getCurrentCommand(){
        return currentCommand;
    }

    public boolean hasFinished() {
        return commandPosition > 1 ||
                command.getArgument() == null
        || (command.getArgument().isSameCommand(MedLinkCommandType.NoCommand) &&
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
                "command=" + command +
                ", commandPosition=" + commandPosition +
                ", functionPosition=" + functionPosition +
                '}';
    }

    public int getNrRetries() {
        return nrRetries;
    }
}
