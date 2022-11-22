package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**

 * BigLogInquirePacket
 */
class BigLogInquirePacket(
    injector: HasAndroidInjector,
    private val start: Int,
    private val end:Int,
    private val delay:Int
) : DiaconnG8Packet(injector ) {
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x72
        aapsLogger.debug(LTag.PUMPCOMM, "BigLogInquirePacket init")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.putShort(start.toShort())
        buffer.putShort(end.toShort())
        buffer.put(delay.toByte())
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_BIG_LOG_INQUIRE"
    }
}