package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryBasalHour
import org.junit.Test

class MsgHistoryBasalHourTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryBasalHour(injector)
        // nothing left to test
    }
}