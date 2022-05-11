package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by dirceu on 17/09/2020.
 */

public enum MedLinkCommandType {

    NoCommand(""),
    ReadCharacteristic("\"ReadCharacteristic\""),
    Notification("SetNotificationBlocking"),
    PumpModel("OK+CONN"),
    Connect("OK+CONN"),
    GetState("S"), //
    StopStartPump("A"),
    Bolus("X", true),
    BolusAmount("BOLUS", true),
    StartPump("START"),
    StopPump("STOP"),
    IsigHistory("I"),
    PreviousIsigHistory("J"),
    BGHistory("C"),
    PreviousBGHistory("T"),
    BolusHistory("H"),
    ActiveBasalProfile("E"),
    BaseProfile("F"),
    Calibrate("K", true),
    CalibrateFrequency("U"),
    CalibrateFrequencyArgument(""),
    CalibrateValue("CAL", true),
    BolusStatus("M"),
    SMBBolus("X", true),
    TBRBolus("X", true),
    PreviousBolusHistory("G",true)
    ;

    public final String code;
    public final boolean needActivePump;
    public Iterator<String> config;
    public Double insulinAmount = 0d;
    public Double bgValue = 0d;
    public Integer resourceId = null;

    public String getCommandDescription() {
        return this.name();
    }
//    MedLinkCommandType(MedLinkCommandType command, Double insulinAmount) {
//        this.code = command.code;
//        this.insulinAmount = insulinAmount;
//    }

    MedLinkCommandType(String command){ this(command,false);
    }

    MedLinkCommandType(String command, boolean needActivePump) {
        this.code = command;
        this.needActivePump = needActivePump;
    }

    private byte[] appendEndLine(StringBuilder buff){
        buff.append("\r").append("\n");
        return buff.toString().getBytes(UTF_8);
    }

    public byte[] getRaw() {
        if(this.config != null && this.config.hasNext()){
            StringBuilder buffer = new StringBuilder(config.next());
            return appendEndLine(buffer);
        }else
        if (this.insulinAmount > 0) {
            StringBuilder buff = new StringBuilder(this.code);
            buff.append(" ");
            if (this.insulinAmount < 10d) {
                buff.append(" ");
            }
            buff.append(this.insulinAmount.toString());

            return appendEndLine(buff);
        } else if(bgValue!=0d){
            StringBuilder buff = new StringBuilder(this.code);
            buff.append(" ");
            if (this.bgValue < 100d) {
                buff.append("0");
            }
            buff.append(this.bgValue.intValue());
            return appendEndLine(buff);
        } else if (this.code != null && !this.code.isEmpty()) {
            return appendEndLine(new StringBuilder(this.code));
        } else {
            return new byte[0];
        }
    }

    public boolean isSameCommand(byte[] command) {
        return command != null && Arrays.equals(this.getRaw(), command);
    }

    public boolean isSameCommand(String command) {
        return command != null && command.equals(this.code);
    }

    public boolean isSameCommand(MedLinkCommandType command) {
        return command != null && (isSameCommand(command.getRaw()) || isSameCommand(command.code));
    }
}
