package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRSPacketHistoryCarbohydrate constructor(
    injector: HasAndroidInjector,
    from: Long = 0
) : DanaRSPacketHistory(injector, from) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override val friendlyName: String = "REVIEW__CARBOHYDRATE"
}