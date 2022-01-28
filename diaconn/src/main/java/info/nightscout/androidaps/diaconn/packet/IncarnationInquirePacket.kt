package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**

 * IncarnationInquirePacket
 */
class IncarnationInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x7A
        aapsLogger.debug(LTag.PUMPCOMM, "IncarnationInquirePacket init")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INCARNATION_INQUIRE"
    }
}