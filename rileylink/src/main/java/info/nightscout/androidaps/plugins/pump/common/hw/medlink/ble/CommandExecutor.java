package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.RemainingBleCommand;

/**
 * Created by Dirceu on 01/04/21.
 */
public abstract class CommandExecutor implements Runnable{
    private final RemainingBleCommand remainingBleCommand;

    protected CommandExecutor(RemainingBleCommand remainingBleCommand) {
        this.remainingBleCommand = remainingBleCommand;

    }

    public RemainingBleCommand getRemainingBleCommand() {
        return remainingBleCommand;
    }
}
