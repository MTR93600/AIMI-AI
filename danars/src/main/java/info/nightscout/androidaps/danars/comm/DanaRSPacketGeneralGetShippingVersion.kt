package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import java.nio.charset.Charset
import javax.inject.Inject

class DanaRSPacketGeneralGetShippingVersion(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_GENERAL__GET_SHIPPING_VERSION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.bleModel = data.copyOfRange(DATA_START, data.size).toString(Charset.forName("US-ASCII"))
        failed = false
        aapsLogger.debug(LTag.PUMPCOMM, "BLE Model: " + danaPump.bleModel)
    }

    override val friendlyName: String = "GENERAL__GET_SHIPPING_VERSION"
}