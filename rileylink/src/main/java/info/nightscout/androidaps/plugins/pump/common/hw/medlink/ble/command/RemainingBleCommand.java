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

    private final UUID charaUUID;

    private final UUID serviceUUID;
    private final Function function;
    private final boolean hasArgument;
//        private final String commandCode;

    public RemainingBleCommand(UUID serviceUUID, UUID charaUUID,
                             byte[] command, Function func, boolean hasArgument) {
        this.serviceUUID = serviceUUID;
        this.charaUUID = charaUUID;
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

    public UUID getCharaUUID() {
        return charaUUID;
    }

    public UUID getServiceUUID() {
        return serviceUUID;
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
