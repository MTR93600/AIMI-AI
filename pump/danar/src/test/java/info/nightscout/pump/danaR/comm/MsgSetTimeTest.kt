package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetTime(injector, System.currentTimeMillis())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}