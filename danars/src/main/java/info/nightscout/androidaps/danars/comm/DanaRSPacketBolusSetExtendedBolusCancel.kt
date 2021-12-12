package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketBolusSetExtendedBolusCancel(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL
        aapsLogger.debug(LTag.PUMPCOMM, "Cancel extended bolus")
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "BOLUS__SET_EXTENDED_BOLUS_CANCEL"
}