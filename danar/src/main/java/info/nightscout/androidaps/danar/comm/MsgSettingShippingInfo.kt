package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag

class MsgSettingShippingInfo(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x3207)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.serialNumber = stringFromBuff(bytes, 0, 10)
        danaPump.shippingDate = dateFromBuff(bytes, 10)
        danaPump.shippingCountry = asciiStringFromBuff(bytes, 13, 3)
        aapsLogger.debug(LTag.PUMPCOMM, "Serial number: " + danaPump.serialNumber)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping date: " + danaPump.shippingDate)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping country: " + danaPump.shippingCountry)
    }
}