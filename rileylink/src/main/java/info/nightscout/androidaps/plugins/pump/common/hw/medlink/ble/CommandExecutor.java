package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.RemainingBleCommand;

/**
 * Created by Dirceu on 01/04/21.
 */
public abstract class CommandExecutor implements Runnable {
    private final RemainingBleCommand remainingBleCommand;
    private final String command;

    protected CommandExecutor(String command, RemainingBleCommand remainingBleCommand) {
        this.remainingBleCommand = remainingBleCommand;
        this.command = command;
    }

    public RemainingBleCommand getRemainingBleCommand() {
        return remainingBleCommand;
    }

    public String getCommand() {
        return command;
    }
}
