package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager;
import info.nightscout.androidaps.utils.DecimalFormatter;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by dirceu on 17/09/2020.
 */

public enum MedLinkCommandType {

    NoCommand(""),
    PumpModel("OK+CONN\r\n"),
    Connect("OK+CONN\r\n"),
    GetState("S\r\n"), //
    StopStartPump("a\r\n"),
    Bolus("X\r\n"),
    BolusAmount("BOLUS "),
    StartPump("START"),
    StopPump("STOP"),
    IsigHistory("I"),
    BGHistory("C"),
    BolusHistory("H"),
    BaseSTDProfile("E\r\n"),
    Enter("\r\n")//Current Active Profile
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
    public Double insulinAmount = 0d;
    public Integer resourceId = null;

    public String getCommandDescription() {
        return this.name();
    }
//    MedLinkCommandType(MedLinkCommandType command, Double insulinAmount) {
//        this.code = command.code;
//        this.insulinAmount = insulinAmount;
//    }

    MedLinkCommandType(String command) {
        this.code = command;
    }


    public byte[] getRaw() {
        if (this.insulinAmount > 0) {
            return (this.code+this.insulinAmount.toString()).getBytes(UTF_8);
        } else if (this.code != null && !this.code.isEmpty()) {
            return this.code.getBytes(UTF_8);
        } else {
            return new byte[0];
        }
    }

    public byte[] buildBolus(double minValueAmount) {

        List< Byte > result = new ArrayList< >();
        // Adding the array one-by-one into the list

        // Converting the list to array and returning the array to the main method

        Byte[] bolus = ArrayUtils.toObject(Bolus.getRaw());

        Byte[] bolusAmount = ArrayUtils.toObject(BolusAmount.getRaw());
        DecimalFormat formatter = new DecimalFormat("00.0");
        Byte[] bolusValue = ArrayUtils.toObject(formatter.format(minValueAmount).replace(",",".").getBytes(UTF_8));
        Stream.of(bolusAmount,bolusValue).flatMap(Stream::of).forEach(result::add);
        return ArrayUtils.toPrimitive(result.toArray(new Byte[result.size()]));

    }
//
//    public MedLinkCommandType buildBolusCommand(double minValueAmount) {
//
//
//        return BolusAmount(Bolus.code,minValueAmount);
//
//    }
}
