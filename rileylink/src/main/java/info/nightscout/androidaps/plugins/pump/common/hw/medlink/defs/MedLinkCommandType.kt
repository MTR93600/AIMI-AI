package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs

import kotlin.jvm.JvmOverloads
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.lang.StringBuilder
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Created by dirceu on 17/09/2020.
 */
enum class MedLinkCommandType @JvmOverloads constructor(command: String, needActivePump: Boolean = false) {

    NoCommand(""),
    ReadCharacteristic("\"ReadCharacteristic\""),
    Notification("SetNotificationBlocking"),
    PumpModel("OK+CONN"),
    Connect("OK+CONN"),
    GetState("S"),  //
    StopStartPump("A"),
    Bolus("X", true),
    BolusAmount("BOLUS", true),
    StartPump("START",true),
    StopPump("STOP",true),
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
    PreviousBolusHistory("G", false);

    @JvmField val code: String?
    val needActivePump: Boolean
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

    fun isSameCommand(command: ByteArray?): Boolean {
        return command != null && Arrays.equals(getRaw(), command)
    }

    fun isSameCommand(command: String?): Boolean {
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
        this.needActivePump = needActivePump
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