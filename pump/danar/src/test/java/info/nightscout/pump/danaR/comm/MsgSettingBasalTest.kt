package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingBasal
import info.nightscout.interfaces.pump.defs.PumpDescription
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgSettingBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(danaRPlugin.pumpDescription).thenReturn(PumpDescription())
        val packet = MsgSettingBasal(injector)

        // test message decoding
        packet.handleMessage(createArray(100, 1.toByte()))
        val expected = packet.intFromBuff(createArray(100, 1.toByte()), 2 * 1, 2)
        Assertions.assertEquals(expected.toDouble() / 100.0, danaPump.pumpProfiles!![danaPump.activeProfile][1], 0.0)
    }
}