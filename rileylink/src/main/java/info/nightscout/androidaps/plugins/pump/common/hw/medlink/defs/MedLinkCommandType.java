package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

import org.apache.commons.lang3.ArrayUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


import static java.nio.charset.StandardCharsets.UTF_8;

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
    StopStartPump("a"),
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
    CalibrateValue("CAL", true),
    BolusStatus("M"),
    SMBBolus("X", true),

//    ,
//    Enter("")//Current Active Profile
    // screenshots z C, H, E, I
//    GetVersion(2), //
//    GetPacket(3), // aka Listen, receive
//    Send(4), //
//    SendAndListen(5), //
//    UpdateRegister(6), //
//    Reset(7), //
//    Led(8),
//    ReadRegister(9),
//    SetModeRegisters(10),
//    SetHardwareEncoding("F"),
//    SetPreamble(12),
//    ResetRadioConfig(13),
//    GetStatistics(14),
    ;

    public final String code;
    private final boolean needActivePump;
    public Double insulinAmount = 0d;
    public int bgValue = 0;
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

    public byte[] getRaw() {
        if (this.insulinAmount > 0) {
            StringBuilder buff = new StringBuilder(this.code);
            buff.append(" ");
            if (this.insulinAmount < 10d) {
                buff.append(" ");
            }
            buff.append(this.insulinAmount.toString());
            buff.append("\r").append("\n");
            return buff.toString().getBytes(UTF_8);
        } else if(bgValue!=0){
            StringBuilder buff = new StringBuilder(this.code);
            buff.append(" ");
            if (this.bgValue < 100) {
                buff.append("0");
            }
            buff.append(this.bgValue);
            buff.append("\r").append("\n");
            return buff.toString().getBytes(UTF_8);
        } else if (this.code != null && !this.code.isEmpty()) {
            return new StringBuilder(this.code).append("\r").append("\n").toString().getBytes(UTF_8);
        } else {
            return new byte[0];
        }
    }


    //
//    public MedLinkCommandType buildBolusCommand(double minValueAmount) {
//
//
//        return BolusAmount(Bolus.code,minValueAmount);
//
//    }
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
