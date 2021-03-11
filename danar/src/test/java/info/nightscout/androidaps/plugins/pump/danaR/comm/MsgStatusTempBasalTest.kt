package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusTempBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusTempBasal(injector)
        // test message decoding
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, danaPump.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(false, danaPump.isTempBasalInProgress)
    }
}