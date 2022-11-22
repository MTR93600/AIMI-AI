package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * BolusSpeedInquirePacket
 */
class BolusSpeedInquirePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x45.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedInquirePacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        return suffixEncode(prefixEncode(msgType, msgSeq, MSG_CON_END))
    }

    override fun getFriendlyName(): String {
        return "PUMP_BOLUS_SPEED_INQUIRE"

    }
}