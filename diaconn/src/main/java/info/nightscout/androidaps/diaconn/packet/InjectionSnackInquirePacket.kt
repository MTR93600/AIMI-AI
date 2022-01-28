package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * InjectionSneckInquirePacket
 */
class InjectionSnackInquirePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x47.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackInquirePacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        return suffixEncode(prefixEncode(msgType, msgSeq, MSG_CON_END))
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_SNACK_INQUIRE"
    }
}