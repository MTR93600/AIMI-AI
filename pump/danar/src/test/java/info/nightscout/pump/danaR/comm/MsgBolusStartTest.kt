package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgBolusStart
import info.nightscout.interfaces.constraints.Constraint
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val packet = MsgBolusStart(injector, 1.0)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(true, packet.failed)

        putByteToArray(array, 0, 2)
        packet.handleMessage(array)
        Assertions.assertEquals(false, packet.failed)
    }
}