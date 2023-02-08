package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command;

import org.jetbrains.annotations.NotNull;

import info.nightscout.interfaces.queue.CustomCommand;

public final class CommandSilenceAlerts implements CustomCommand {
    @NotNull @Override public String getStatusDescription() {
        return "ACKNOWLEDGE ALERTS";
    }
}
