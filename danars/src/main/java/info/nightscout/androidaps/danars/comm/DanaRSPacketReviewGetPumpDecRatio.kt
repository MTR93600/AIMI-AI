package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketReviewGetPumpDecRatio(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_DEC_RATIO
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.decRatio = intFromBuff(data, 0, 1) * 5
        failed = false
        aapsLogger.debug(LTag.PUMPCOMM, "Dec ratio: ${danaPump.decRatio}%")
    }

    override val friendlyName: String = "REVIEW__GET_PUMP_DEC_RATIO"
}