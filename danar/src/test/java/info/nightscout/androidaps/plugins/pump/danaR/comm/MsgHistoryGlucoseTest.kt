package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryGlucose
import org.junit.Test

class MsgHistoryGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryGlucose(injector)
        // nothing left to test
    }
}