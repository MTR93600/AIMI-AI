package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag

class MsgHistoryRefill(
    injector: HasAndroidInjector
) : MsgHistoryAll(injector) {

    init {
        SetCommand(0x3108)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }
    // Handle message taken from MsgHistoryAll
}