package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryDailyInsulin
import org.junit.Test

class MsgHistoryDailyInsulinTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryDailyInsulin(injector)
        // nothing left to test
    }
}