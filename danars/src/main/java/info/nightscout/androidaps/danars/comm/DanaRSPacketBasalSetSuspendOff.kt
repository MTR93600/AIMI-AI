package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketBasalSetSuspendOff(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF
        aapsLogger.debug(LTag.PUMPCOMM, "Turning off suspend")
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

    override val friendlyName: String = "BASAL__SET_SUSPEND_OFF"
}