package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs

import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Created by dirceu on 17/09/2020.
 */
enum class MedLinkCommandType constructor(command: String, val needActivePump: Boolean = false, val listCommand: Boolean = false) {

    NoCommand(""),
    ReadCharacteristic("\"ReadCharacteristic\"", listCommand = true),
    Notification("SetNotificationBlocking", listCommand = true),
    PumpModel("OK+CONN", listCommand = true),
    Connect("OK+CONN", listCommand = true),
    GetState("S", listCommand = true),  //
    StopStartPump("A"),
    Bolus("X", true),
    BolusAmount("BOLUS", true),
    StartPump("START",true),
    StopPump("STOP",true),
    IsigHistory("I", listCommand = true),
    PreviousIsigHistory("J", listCommand = true),
    BGHistory("C", listCommand = true),
    PreviousBGHistory("T", listCommand = true),
    BolusHistory("H"),
    ActiveBasalProfile("E", listCommand = true),
    BaseProfile("F", listCommand = true),
    Calibrate("K", true),
    CalibrateFrequency("U"),
    CalibrateFrequencyArgument(""),
    CalibrateValue("CAL", true),
    BolusStatus("M"),
    SMBBolus("X", true),
    TBRBolus("X", true),
    PreviousBolusHistory("G", listCommand = true);

    @JvmField val code: String?
    var config: Iterator<String>? = null
    var insulinAmount = 0.0
    var bgValue = 0.0
    var resourceId: Int = 0
    fun getCommandDescription(): String {
        return name
    }

    private fun appendEndLine(buff: StringBuilder): ByteArray {
        buff.append("\r").append("\n")
        return buff.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun getRaw(argument: String = ""): ByteArray {
        // if (config != null && config!!.hasNext()) {
        //     val buffer = StringBuilder(config!!.next())
        //     appendEndLine(buffer)
        // } else
        return  if (this.code != null && this.code.isNotEmpty()) {
            appendEndLine(StringBuilder(this.code).append(argument))
        } else {
            ByteArray(0)
        }
    }

    private fun isSameCommand(command: ByteArray?): Boolean {
        return command != null && Arrays.equals(getRaw(), command)
    }

    private fun isSameCommand(command: String?): Boolean {
        return command != null && command == this.code
    }

    fun isSameCommand(command: MedLinkCommandType?): Boolean {
        return command != null && (isSameCommand(command.getRaw()) || isSameCommand(command.code))
    }

    //    MedLinkCommandType(MedLinkCommandType command, Double insulinAmount) {
    //        this.code = command.code;
    //        this.insulinAmount = insulinAmount;
    //    }
    init {
        this.code = command
    }

    override fun toString(): String {
        return when {
            (this == BolusAmount) -> {
                super.toString() +
                    "bolusAmount: $insulinAmount"
            }
            (this == CalibrateFrequencyArgument) -> {
                super.toString() +
                    "calibrateArgument: $"

            }

            (this == CalibrateValue) -> {
                super.toString() +
                    "bgValue: $bgValue"

            }

            else                                 -> {
                super.toString()
            }
        }
    }
}