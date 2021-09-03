package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;

/**
 * Created by Dirceu on 27/03/21.
 */
public class RemainingBleCommand {

    private final byte[] command;

    private final Function function;
    private final boolean hasArgument;
//        private final String commandCode;

    public RemainingBleCommand(byte[] command, Function func, boolean hasArgument) {
        this.command = command;
        this.function = func;
        this.hasArgument = hasArgument;
    }

    public Function getFunction() {
        return function;
    }

//        public byte[] getCommand() {
//            return command;
//        }

    public byte[] getCommand() {
        return command;
    }

    @Override public String toString() {
        return "RemainingBleCommand{" +
                "command=" + new String(command, StandardCharsets.UTF_8) +
                '}';
    }

    public boolean hasArg() {
        return hasArgument;
    }
}
