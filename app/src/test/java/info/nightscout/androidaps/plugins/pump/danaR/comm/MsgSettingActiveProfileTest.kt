package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingActiveProfile
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingActiveProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingActiveProfile(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(packet.intFromBuff(createArray(34, 7.toByte()), 0, 1), danaPump.activeProfile)
    }
}