package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

/**
 * Created by dirceu on 17/09/2020.
 */

public enum MedLinkCommandType {

    NoCommand(""),
    Connect("OK+CONN"),

    GetState("S"), //
    StopStartPump("a"),
    Bolus("X"),
    IsigHistory("I"),
    BGHistory("C"),
    BolusHistory("H"),
    BaseSTDProfile("E")
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

    public String code;


    MedLinkCommandType(String command) {
        this.code = code;
    }
}
