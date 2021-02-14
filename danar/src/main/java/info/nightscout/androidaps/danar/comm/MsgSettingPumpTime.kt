package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import org.joda.time.DateTime
import java.util.*

class MsgSettingPumpTime(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x320A)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val time = DateTime(
            2000 + intFromBuff(bytes, 5, 1),
            intFromBuff(bytes, 4, 1),
            intFromBuff(bytes, 3, 1),
            intFromBuff(bytes, 2, 1),
            intFromBuff(bytes, 1, 1),
            intFromBuff(bytes, 0, 1)
        ).millis
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time) + " Phone time: " + Date())
        danaPump.setPumpTime(time)
    }

    override fun handleMessageNotReceived() {
        super.handleMessageNotReceived()
        danaPump.resetPumpTime()
    }
}