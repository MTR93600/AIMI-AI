package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketApsSetEventHistoryTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_APS_Set_Event_History) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() { // test for negative carbs
        val now = DateUtil.now()
        var historyTest = DanaRS_Packet_APS_Set_Event_History(packetInjector, info.nightscout.androidaps.dana.DanaPump.CARBS, now, -1, 0)
        var testParams = historyTest.requestParams
        Assert.assertEquals(0.toByte(), testParams[8])
        // 5g carbs
        historyTest = DanaRS_Packet_APS_Set_Event_History(packetInjector, info.nightscout.androidaps.dana.DanaPump.CARBS, now, 5, 0)
        testParams = historyTest.requestParams
        Assert.assertEquals(5.toByte(), testParams[8])
        // 150g carbs
        historyTest = DanaRS_Packet_APS_Set_Event_History(packetInjector, info.nightscout.androidaps.dana.DanaPump.CARBS, now, 150, 0)
        testParams = historyTest.requestParams
        Assert.assertEquals(150.toByte(), testParams[8])
        // test message generation
        historyTest = DanaRS_Packet_APS_Set_Event_History(packetInjector, info.nightscout.androidaps.dana.DanaPump.CARBS, now, 5, 0)
        testParams = historyTest.requestParams
        Assert.assertEquals(5.toByte(), testParams[8])
        Assert.assertEquals(11, testParams.size)
        Assert.assertEquals(info.nightscout.androidaps.dana.DanaPump.CARBS.toByte(), testParams[0])
        // test message decoding
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, historyTest.failed)
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, historyTest.failed)
        Assert.assertEquals("APS_SET_EVENT_HISTORY", historyTest.friendlyName)
    }
}