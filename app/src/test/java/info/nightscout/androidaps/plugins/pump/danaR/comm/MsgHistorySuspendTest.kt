package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistorySuspend
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistorySuspendTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistorySuspend(injector)
        // nothing left to test
    }
}