package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistorySuspend
import org.junit.Test

class MsgHistorySuspendTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistorySuspend(injector)
        // nothing left to test
    }
}