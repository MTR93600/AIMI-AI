package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingBasalProfileAll
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingBasalProfileAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingBasalProfileAll(injector)

        // test message decoding
        packet.handleMessage(createArray(400, 1.toByte()))
    }
}