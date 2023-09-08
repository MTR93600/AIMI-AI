package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingGlucose
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingGlucose(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(1, danaPump.units)
    }
}